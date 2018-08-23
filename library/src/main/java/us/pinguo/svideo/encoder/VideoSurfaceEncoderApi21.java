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
public class VideoSurfaceEncoderApi21 extends VideoSurfaceEncoder {

    private static final int MAX_LOOP_COUNT = 10;

    public VideoSurfaceEncoderApi21(int width, int height, int bitRate, int frameRate, int iFrameInterval, MediaMuxer mediaMuxer) {
        super(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
    }

    @Override
    public void drainEncoder(boolean endOfStream, long timeUs) {
        if (VERBOSE) {
            RL.d("drainEncoder(" + endOfStream + ")");
        }

        if (endOfStream) {
            if (VERBOSE) {
                RL.d("sending EOS to encoder");
            }
            mMediaCodec.signalEndOfInputStream();
        }
        //拿取编码好的数据
        int loopCount = 0;
        while (true) {
            loopCount++;
            int outputBufferId = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (outputBufferId >= 0) {
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
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    return;
                }
                RL.i("mBufferInfo,flags:" + mBufferInfo.flags + " offset" + mBufferInfo.offset + " presentationTimeUs：" + mBufferInfo.presentationTimeUs
                        + " size:" + mBufferInfo.size);
                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM && mBufferInfo.presentationTimeUs < 0) {
                    mBufferInfo.presentationTimeUs = 0;
                }
                mMuxer.writeSampleData(mTrackIndex, outputBuffer, mBufferInfo);
                mRecordedFrames++;
                if (VERBOSE) {
                    RL.d("sent " + mBufferInfo.size + " bytes to muxer");
                }
                mMediaCodec.releaseOutputBuffer(outputBufferId, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        RL.w("reached end of stream unexpectedly");
                    } else {
                        if (VERBOSE) {
                            RL.d("end of stream reached");
                        }
                    }
                    break;
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                MediaFormat outputFormat = mMediaCodec.getOutputFormat(); // option B
                RL.i("INFO_OUTPUT_FORMAT_CHANGED:" + outputFormat);
            } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    if (VERBOSE) {
                        RL.d("no output available, spinning to await EOS");
                    }
                }
            } else {
                RL.e("outputBufferId:" + outputBufferId + " break;");
            }

            if (loopCount > MAX_LOOP_COUNT) {
                RL.et("hwLog","loopCount:" + loopCount + ",outputBufferId:" + outputBufferId);
            }
        }

        //计算录制时间
        if (mOnRecordProgressListener != null) {
            int recordedDuration = (int) ((1000f / mFrameRate) * mRecordedFrames);
            mOnRecordProgressListener.onRecording(recordedDuration);
        }

    }
}
