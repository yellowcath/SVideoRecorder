package us.pinguo.svideo.encoder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import us.pinguo.svideo.encoder.VideoMediaEncoderThread.SaveRequest;
import us.pinguo.svideo.recorder.SMediaCodecRecorder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.SVideoUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 这个貌似写的不到位，没啥优势，先不用
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class VideoEncoderApi21Async {
    private static final boolean VERBOSE = true; // lots of logging
    // parameters for the encoder
    public static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video

    private int mWidth;
    private int mHeight;
    private MediaCodec mMediaCodec;
    private MediaMuxer mMuxer;
    private int mTrackIndex = -1;
    private boolean mMuxerStarted;
    byte[] mFrameData;
    //    private int mColorFormat;
    private long mStartTime = 0;

    private int mFrameRate;

    private int mRecordedFrames;
    private OnRecordProgressListener mOnRecordProgressListener;
    private boolean mInited;

    private int mBitRate;
    private int mIFrameInterval;

    private MediaFormat mNewFormat;
    private LinkedBlockingQueue<SaveRequest> mQueue = new LinkedBlockingQueue<>();
    private int mCurInputBufferId = -1;
    private VideoMediaEncoderApi21Thread mVideoMediaEncoderThread;
    private CountDownLatch mCountDownLatch;

    public VideoEncoderApi21Async(int width, int height, int bitRate, int frameRate, int iFrameInterval, MediaMuxer mediaMuxer) {
        RL.i("VideoEncoder()");
        this.mWidth = width;
        this.mHeight = height;
        mBitRate = bitRate;
        mIFrameInterval = iFrameInterval;
        mFrameRate = frameRate;
        mFrameData = new byte[this.mWidth * this.mHeight * 3 / 2];

        mStartTime = System.nanoTime();

        mMuxer = mediaMuxer;
        mTrackIndex = -1;
        mMuxerStarted = false;
        mRecordedFrames = 0;
    }

    public void setVideoMediaEncoderThread(VideoMediaEncoderApi21Thread thread) {
        mVideoMediaEncoderThread = thread;
    }

    public void addImageData(final SaveRequest saveRequest) {
        if (!mInited) {
            mInited = true;
            initInThread();
        }
        mQueue.add(saveRequest);
        if (mCurInputBufferId == -1) {
            return;
        }
        enqueueBufferData(mMediaCodec, mCurInputBufferId, mQueue.poll());
        mCurInputBufferId = -1;
    }

    private void enqueueBufferData(MediaCodec codec, int inputBufferId, SaveRequest saveRequest) {
        RL.i("enqueueBufferData");

        byte[] input = saveRequest.data;
        int inputLen = mWidth * mHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
        if (input.length < inputLen) {
            inputLen = input.length;
        }
        //YUV格式转换
        System.arraycopy(input, 0, mFrameData, 0, inputLen);
        SVideoUtil.convertColorFormat(mFrameData, mWidth, mHeight, inputLen, SVideoUtil.getSupportColorFormat());

        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
        inputBuffer.clear();
        inputBuffer.put(mFrameData);
        codec.queueInputBuffer(inputBufferId, 0,
                mFrameData.length, saveRequest.fpsTimeUs, 0);

    }

    private void initInThread() {
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
        } catch (IOException e) {
            RL.e(e);
        }
//                MediaCodec.createByCodecName(codecInfo.getName());
        mMediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mNewFormat = mMediaCodec.getOutputFormat();
        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int inputBufferId) {
                RL.i("onInputBufferAvailable:" + inputBufferId);
                SaveRequest saveRequest = mQueue.poll();
                if (saveRequest == null) {
                    //暂时无数据
                    mCurInputBufferId = inputBufferId;
                } else {
                    //有数据可以编码
                    enqueueBufferData(codec, inputBufferId, saveRequest);
                }
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, BufferInfo info) {
                RL.i("onOutputBufferAvailable:" + outputBufferId);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) {
                        RL.d("ignoring BUFFER_FLAG_CODEC_CONFIG");
                    }
                    info.size = 0;
                }
                if (info.size <= 0) {
                    RL.d("mBufferInfo.size<=0 return.");
                    return;
                }

                if (!mMuxerStarted) {
//						throw new RuntimeException("muxer hasn't started");
                    MediaFormat bufferFormat = mMediaCodec.getOutputFormat(outputBufferId);
                    mTrackIndex = mMuxer.addTrack(bufferFormat);
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

                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);

                mMuxer.writeSampleData(mTrackIndex, outputBuffer, info);
                if (VERBOSE) {
                    RL.d("sent " + info.size + " bytes to muxer");
                }
                codec.releaseOutputBuffer(outputBufferId, false);

                mRecordedFrames++;
                RL.i("总帧数:" + mRecordedFrames);
                //计算录制时间
                if (mOnRecordProgressListener != null) {
                    int recordedDuration = (int) ((1000f / mFrameRate) * mRecordedFrames);
                    mOnRecordProgressListener.onRecording(recordedDuration);
                }
                if (mQueue.size() == 0 && mCountDownLatch != null) {
                    RL.i("最后一帧编码完成，结束等待");
                    mCountDownLatch.countDown();
                }
            }


            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                RL.e("onError" + e.getMessage());
                if (mVideoMediaEncoderThread != null) {
                    mVideoMediaEncoderThread.throwRecordError(e);
                }
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                RL.i("onOutputFormatChanged" + format);
                mNewFormat = format;
            }
        });
        mMediaCodec.start();
    }

    public void waitFinish() {
        RL.i("waitFinish");
        mCountDownLatch = new CountDownLatch(1);
        try {
            RL.i("等待编码完:" + mQueue.size());
            mCountDownLatch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        RL.i("数据编码完成或1秒时间到，还剩" + mQueue.size());
    }

    @SuppressLint("NewApi")
    public void close() {
        RL.it("VideoMediaEncoderThread", "+close()");
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
        } catch (Exception e) {
            RL.e(e);
        }
        if (mMuxer != null) {
            /** stop() throws an exception if you haven't fed it any data.  Keep track
             of frames submitted, and don't call stop() if we haven't written anything.*/
            try {
                SMediaCodecRecorder.sStopSemaphore.release(1);
//                L.it("VideoMediaEncoderThread", "acquire,availablePermits:" + SMediaCodecRecorder.sSemaphore.availablePermits());
                SMediaCodecRecorder.sStopSemaphore.acquire(2);
//                L.it("VideoMediaEncoderThread", "acquire,ok:");
                if (!SMediaCodecRecorder.sMuxerStopped) {
                    SMediaCodecRecorder.sMuxerStopped = true;
//                    L.it("VideoMediaEncoderThread", "mMuxer.stop:");
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

    public int getRecordedFrames() {
        return mRecordedFrames;
    }

    public void setOnRecordProgressListener(OnRecordProgressListener onRecordProgressListener) {
        this.mOnRecordProgressListener = onRecordProgressListener;
    }
}
