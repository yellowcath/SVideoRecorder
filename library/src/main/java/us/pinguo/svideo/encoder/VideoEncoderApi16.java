package us.pinguo.svideo.encoder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Build;
import us.pinguo.svideo.recorder.RecordFailException;
import us.pinguo.svideo.utils.RL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 使用MediaCodec录制YUV数据，每次使用都要重新new一个
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class VideoEncoderApi16 extends VideoEncoderFromBuffer {

    private FileChannel mFileChannel;
    @SuppressLint("NewApi")
    public VideoEncoderApi16(int width, int height, int bitRate, int frameRate, int iFrameInterval, String path) {
        super(width, height, bitRate, frameRate, iFrameInterval, null);

        File file = new File(path);
        try {
            file.createNewFile();
            mFileChannel = new FileOutputStream(file).getChannel();
        } catch (IOException e) {
            throw new RecordFailException(e);
        }
    }

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

//                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                    // The codec config data was pulled out and fed to the muxer when we got
//                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//                    if (VERBOSE) {
//                        L.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
//                    }
//                    mBufferInfo.size = 0;
//                }

                if (mBufferInfo.size != 0) {

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    if (mFileChannel != null) {
                        try {
                            mFileChannel.write(outputBuffer);
                        } catch (IOException e1) {
                            RL.e(e1);
                        }
                    }
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
        try {
            if (mMediaCodec != null && mCodecStarted) {
                mCodecStarted = false;
                mMediaCodec.stop();
                mMediaCodec.release();
            }
        } catch (Exception e) {
            RL.e(e);
        }
        if (mFileChannel != null) {
            try {
                mFileChannel.close();
            } catch (IOException e) {
                RL.e(e);
            }
        }
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

    public void setOnRecordProgressListener(OnRecordProgressListener onRecordProgressListener) {
        this.mOnRecordProgressListener = onRecordProgressListener;
    }
}
