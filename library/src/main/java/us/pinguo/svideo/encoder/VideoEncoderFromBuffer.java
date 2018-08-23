package us.pinguo.svideo.encoder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import us.pinguo.svideo.recorder.SMediaCodecRecorder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.SVideoUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 使用MediaCodec录制YUV数据，每次使用都要重新new一个
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class VideoEncoderFromBuffer {
    protected static final boolean VERBOSE = true; // lots of logging
    // parameters for the encoder
    public static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    // I-frames
    protected static final int TIMEOUT_USEC = 10000;

    protected int mWidth;
    protected int mHeight;
    protected MediaCodec mMediaCodec;
    protected MediaMuxer mMuxer;
    protected BufferInfo mBufferInfo;
    protected int mTrackIndex = -1;
    protected boolean mMuxerStarted;
    protected byte[] mFrameData;

    protected int mFrameRate;

    protected int mRecordedFrames;
    protected OnRecordProgressListener mOnRecordProgressListener;

    protected int mBitRate;
    protected int mIFrameInterval;

    protected MediaFormat mNewFormat;
    protected boolean mCodecStarted;

    protected long totalTime;
    protected int index;

    @SuppressLint("NewApi")
    public VideoEncoderFromBuffer(int width, int height, int bitRate, int frameRate, int iFrameInterval, MediaMuxer mediaMuxer) {
        this.mWidth = width;
        this.mHeight = height;
        mBitRate = bitRate;
        mIFrameInterval = iFrameInterval;
        mFrameRate = frameRate;

        mBufferInfo = new BufferInfo();
        mMuxer = mediaMuxer;
        mTrackIndex = -1;
        mMuxerStarted = false;
        mRecordedFrames = 0;
    }

    public void initInThread() {
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, SVideoUtil.getSupportColorFormat());
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                mIFrameInterval);
        if (VERBOSE) {
            RL.d("format: " + mediaFormat);
        }

        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mMediaCodec.configure(mediaFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            RL.e(e);
        } finally {
            mCodecStarted = true;
        }
    }

    /**
     * YUV数据格式转换
     *
     * @param input
     */
    protected void convertColorFormat(byte[] input) {
        int inputLen = mWidth * mHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
        if (input.length < inputLen) {
            inputLen = input.length;
        }
        long s = System.currentTimeMillis();
        System.arraycopy(input, 0, mFrameData, 0, inputLen);
        SVideoUtil.convertColorFormat(mFrameData, mWidth, mHeight, inputLen, SVideoUtil.getSupportColorFormat());
        long e = System.currentTimeMillis();
        RL.i("convertColorFormat耗时:" + (e - s) + "ms");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void encodeFrame(byte[] input, long fpsTimeUs) {
        RL.i("encodeFrame()");
        convertColorFormat(input);

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (VERBOSE) {
            RL.i("inputBufferIndex-->" + inputBufferIndex);
        }
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0,
                    mFrameData.length, fpsTimeUs, 0);
        } else {
            // either all in use, or we timed out during initial setup
            if (VERBOSE) {
                RL.d("input buffer not available");
            }
        }

        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        RL.i("outputBufferIndex-->" + outputBufferIndex);
        do {
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE) {
                    RL.d("no output from encoder available");
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                outputBuffers = mMediaCodec.getOutputBuffers();
                if (VERBOSE) {
                    RL.d("encoder output buffers changed");
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder

                mNewFormat = mMediaCodec.getOutputFormat();
                RL.d("encoder output format changed: " + mNewFormat);
                /**放到下面start，方便同步*/
//                // now that we have the Magic Goodies, start the muxer
//                mTrackIndex = mMuxer.addTrack(newFormat);
//                mMuxer.start();
//                mMuxerStarted = true;
            } else if (outputBufferIndex < 0) {
                RL.w("unexpected result from encoder.dequeueOutputBuffer: " +
                        outputBufferIndex);
                // let's ignore it
            } else {
                if (VERBOSE) {
                    RL.d("perform encoding");
                }
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) {
                        RL.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
                    }
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {

                    if (!mMuxerStarted) {
//						throw new RuntimeException("muxer hasn't started");
                        MediaFormat newFormat = mMediaCodec.getOutputFormat();
                        mTrackIndex = mMuxer.addTrack(newFormat);
                        SMediaCodecRecorder.sStartSemaphore.release(1);
                        try {
                            SMediaCodecRecorder.sStartSemaphore.acquire(2);
                            if (!SMediaCodecRecorder.sMuxerStarted) {
                                if (!SMediaCodecRecorder.sMuxerStarted) {
                                    SMediaCodecRecorder.sMuxerStarted = true;
                                    mMuxer.start();
                                }
                            }
                        } catch (InterruptedException e1) {
                            RL.e(e1);
                        } finally {
                            SMediaCodecRecorder.sStartSemaphore.release(2);
                        }
                        mMuxerStarted = true;
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

//                    synchronized (mMuxer) {
                    mMuxer.writeSampleData(mTrackIndex, outputBuffer, mBufferInfo);
//                    }
                    if (VERBOSE) {
                        RL.d("sent " + mBufferInfo.size + " bytes to muxer");
                    }
                }

                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        }
        while (outputBufferIndex >= 0);
        mRecordedFrames++;
        //计算录制时间
        if (mOnRecordProgressListener != null)

        {
            int recordedDuration = (int) ((1000f / mFrameRate) * mRecordedFrames);
            mOnRecordProgressListener.onRecording(recordedDuration);
        }
    }

    @SuppressLint("NewApi")
    public void close() {
        RL.i("close");
        try {
            if (mMediaCodec != null && mCodecStarted) {
                mCodecStarted = false;
                mMediaCodec.stop();
                mMediaCodec.release();
            }
        } catch (Exception e) {
            RL.e(e);
        }
        if (mMuxer != null) {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            try {
                SMediaCodecRecorder.sStopSemaphore.release(1);
//                RL.it("VideoMediaEncoderThread", "acquire,availablePermits:" + SMediaCodecRecorder.sStopSemaphore.availablePermits());
                SMediaCodecRecorder.sStopSemaphore.acquire(2);
//                RL.it("VideoMediaEncoderThread", "acquire,ok:");
                if (!SMediaCodecRecorder.sMuxerStopped) {
                    SMediaCodecRecorder.sMuxerStopped = true;
                    RL.it("VideoMediaEncoderThread", "mMuxer.stop:");
                    mMuxer.stop();
                }
            } catch (Exception e) {
                RL.e(e);
            } finally {
//                L.it("VideoMediaEncoderThread", "+ mMuxer.release()");
                mMuxer.release();
//                L.it("VideoMediaEncoderThread", "- mMuxer.release()");
//                L.it("VideoMediaEncoderThread", "+release2");
                SMediaCodecRecorder.sStopSemaphore.release(2);
//                L.it("VideoMediaEncoderThread", "-release2");
            }
//            L.it("VideoMediaEncoderThread", "-close");
            mMuxer = null;
        }
    }

    /**
     * NV21 is a 4:2:0 YCbCr, For 1 NV21 pixel: YYYYYYYY VUVU I420YUVSemiPlanar
     * is a 4:2:0 YUV, For a single I420 pixel: YYYYYYYY UVUV Apply NV21 to
     * I420YUVSemiPlanar(NV12) Refer to https://wiki.videolan.org/YUV/
     */
    private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes,
                                      int width, int height, int inputLen) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, inputLen);
        for (int i = width * height; i < inputLen; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

    /**
     * Returns a color format that is supported by the codec and by this test
     * code. If no match is found, this throws a test failure -- the set of
     * formats known to the test should be expanded for new platforms.
     * ps:这个函数貌似在线程里跑非常非常慢
     */
    public static int selectColorFormat(MediaCodecInfo codecInfo,
                                        String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        //优先选NV21，因为已有线程的YUV转换函数
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                return colorFormat;
            }
        }
        //没有再选择其它
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        RL.e("couldn't find a good color format for " + codecInfo.getName()
                + " / " + mimeType);
        return 0; // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands
     * (i.e. we know how to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or
     * null if no match was found.
     */
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex, int frameRate) {
        return 132 + frameIndex * 1000000 / frameRate;
    }

    public int getRecordedFrames() {
        return mRecordedFrames;
    }

    /**
     * Returns true if the specified color format is semi-planar YUV. Throws an
     * exception if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

    public void setOnRecordProgressListener(OnRecordProgressListener onRecordProgressListener) {
        this.mOnRecordProgressListener = onRecordProgressListener;
    }
}
