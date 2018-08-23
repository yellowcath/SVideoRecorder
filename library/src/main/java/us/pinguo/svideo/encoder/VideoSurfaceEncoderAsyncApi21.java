package us.pinguo.svideo.encoder;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import us.pinguo.svideo.recorder.SMediaCodecRecorder;
import us.pinguo.svideo.utils.RL;

import java.nio.ByteBuffer;

/**
 * Created by huangwei on 2016/4/13.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class VideoSurfaceEncoderAsyncApi21 extends VideoSurfaceEncoder {
    private MediaCodec.Callback mCallback;

    public VideoSurfaceEncoderAsyncApi21(int width, int height, int bitRate, int frameRate, int iFrameInterval, MediaMuxer mediaMuxer) {
        super(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
        mCallback = new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                RL.i("onInputBufferAvailable");
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, MediaCodec.BufferInfo info) {
                if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    RL.i("BUFFER_FLAG_CODEC_CONFIG.return.");
                    return;
                }
                long s = System.currentTimeMillis();
                ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferId);
                if (!mMuxerStarted) {
//						throw new RuntimeException("muxer hasn't started");
                    MediaFormat bufferFormat = mMediaCodec.getOutputFormat();
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
                        mMuxerStarted = true;
                    }
                }
                RL.i("info,flags:" + info.flags + " offset" + info.offset + " presentationTimeUs：" + info.presentationTimeUs
                        + " size:" + info.size);
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM && mBufferInfo.presentationTimeUs < 0) {
                    mBufferInfo.presentationTimeUs = 0;
                }
                mMuxer.writeSampleData(mTrackIndex, outputBuffer, info);
                mRecordedFrames++;
                if (VERBOSE) {
                    RL.d("sent " + info.size + " bytes to muxer");
                }
                mMediaCodec.releaseOutputBuffer(outputBufferId, false);
                //计算录制时间
                if (mOnRecordProgressListener != null) {
                    int recordedDuration = (int) ((1000f / mFrameRate) * mRecordedFrames);
                    mOnRecordProgressListener.onRecording(recordedDuration);
                }
                long e = System.currentTimeMillis();
                RL.i("耗时:" + (e - s) + "ms");
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                RL.e(e);
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                MediaFormat outputFormat = mMediaCodec.getOutputFormat();
                RL.i("INFO_OUTPUT_FORMAT_CHANGED:" + outputFormat);
            }
        };
    }

    @Override
    protected void afterMediaCodecCreated() {
        mMediaCodec.setCallback(mCallback);
    }

    @Override
    public void drainEncoder(boolean endOfStream,long timeUs) {
        if (endOfStream) {
            if (VERBOSE) {
                RL.d("sending EOS to encoder");
            }
            mMediaCodec.signalEndOfInputStream();
        }
    }
}
