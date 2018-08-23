package us.pinguo.svideo.encoder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.view.Surface;
import us.pinguo.svideo.recorder.SMediaCodecRecorder;
import us.pinguo.svideo.utils.RL;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 使用MediaCodec录制Surface数据，每次使用都要重新new一个
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoSurfaceEncoder {
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

    protected int mRecordedFrames;

    protected int mFrameRate;
    protected OnRecordProgressListener mOnRecordProgressListener;

    protected int mBitRate;
    protected int mIFrameInterval;

    protected MediaFormat mNewFormat;
    protected boolean mCodecStarted;
    private Surface mInputSurface;

    protected long totalTime;
    protected int index;

    @SuppressLint("NewApi")
    public VideoSurfaceEncoder(int width, int height, int bitRate, int frameRate, int iFrameInterval, MediaMuxer mediaMuxer) {
        this.mWidth = width;
        this.mHeight = height;
        mBitRate = bitRate;
        mIFrameInterval = iFrameInterval;
        mFrameRate = frameRate;
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];

        mBufferInfo = new BufferInfo();

        mMuxer = mediaMuxer;
        mTrackIndex = -1;
        mMuxerStarted = false;
        mRecordedFrames = 0;
    }

    public void initInThread() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                this.mWidth, this.mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                mIFrameInterval);
        if (VERBOSE) {
            RL.d("format: " + mediaFormat);
        }

        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            afterMediaCodecCreated();
            mMediaCodec.configure(mediaFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mMediaCodec.createInputSurface();
            mMediaCodec.start();
        } catch (IOException e) {
            RL.e(e);
        } finally {
            mCodecStarted = true;
        }
    }

    protected void afterMediaCodecCreated() {
    }

    public void drainEncoder(boolean endOfStream,long timeUs) {
        final int TIMEOUT_USEC = 10000;
        if (VERBOSE) {
            RL.d("drainEncoder(" + endOfStream + ")");
        }
        if (endOfStream) {
            if (VERBOSE) {
                RL.d("sending EOS to encoder");
            }
            mMediaCodec.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        while (true) {
            int encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) {
                        RL.d("no output available, spinning to await EOS");
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                mNewFormat = mMediaCodec.getOutputFormat();
                RL.d("encoder output format changed: " + mNewFormat);
            } else if (encoderStatus < 0) {
                RL.w("unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
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
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    if(mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM && mBufferInfo.presentationTimeUs<0){
                        mBufferInfo.presentationTimeUs = 0;
                    }
                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    mRecordedFrames++;
                    if (VERBOSE) {
                        RL.d("sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                                mBufferInfo.presentationTimeUs);
                    }
                }

                mMediaCodec.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        RL.w("reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) {
                            RL.d("end of stream reached");
                        }
                    }
                    break;      // out of while
                }
            }
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
            try {
                SMediaCodecRecorder.sStopSemaphore.release(1);
                SMediaCodecRecorder.sStopSemaphore.acquire(2);
                if (!SMediaCodecRecorder.sMuxerStopped) {
                    SMediaCodecRecorder.sMuxerStopped = true;
                    RL.it("VideoMediaEncoderThread", "mMuxer.stop:");
                    mMuxer.stop();
                }
            } catch (Exception e) {
                RL.e(e);
            } finally {
                mMuxer.release();
                SMediaCodecRecorder.sStopSemaphore.release(2);
            }
            mMuxer = null;
        }
    }


    public int getRecordedFrames() {
        return mRecordedFrames;
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void setOnRecordProgressListener(OnRecordProgressListener onRecordProgressListener) {
        this.mOnRecordProgressListener = onRecordProgressListener;
    }
}
