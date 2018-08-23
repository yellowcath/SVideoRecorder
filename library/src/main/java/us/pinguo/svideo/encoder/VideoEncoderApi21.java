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
 * 录的时候有问题，MP4文件录出来时长单位是微秒。。。
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class VideoEncoderApi21 extends VideoEncoderFromBuffer {

    public VideoEncoderApi21(int width, int height, int bitRate, int frameRate, int iFrameInterval, String path, MediaMuxer mediaMuxer) {
        super(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
    }

    public void encodeFrame(byte[] input, long fpsTimeUs) {
        RL.i("encodeFrame,fpsTimeUs:"+fpsTimeUs);
        //YUV数据格式转换
        convertColorFormat(input);

        //输入原始数据
        int inputBufferId = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufferId >= 0) {
            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferId);
            inputBuffer.put(mFrameData);
            mMediaCodec.queueInputBuffer(inputBufferId, 0,
                    mFrameData.length, fpsTimeUs, 0);
        } else {
            // either all in use, or we timed out during initial setup
            if (VERBOSE) {
                RL.d("input buffer not available");
            }
        }
        //拿取编码好的数据
        int outputBufferId = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        do {
            if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (VERBOSE) {
                    RL.d("no output from encoder available");
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder

                mNewFormat = mMediaCodec.getOutputFormat();
                RL.d("encoder output format changed: " + mNewFormat);
                /**放到下面start，方便同步*/
//                // now that we have the Magic Goodies, start the muxer
//                mTrackIndex = mMuxer.addTrack(newFormat);
//                mMuxer.start();
//                mMuxerStarted = true;
            } else if (outputBufferId < 0) {
                RL.w("unexpected result from encoder.dequeueOutputBuffer: " +
                        outputBufferId);
                // let's ignore it
            } else {
                if (VERBOSE) {
                    RL.d("perform encoding");
                }
                ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferId);
                if (outputBuffer == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferId +
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
                        RL.d("sent " + mBufferInfo.size + " bytes to muxer,timsUs:"+mBufferInfo.presentationTimeUs);
                    }
                }

                mMediaCodec.releaseOutputBuffer(outputBufferId, false);
            }
            outputBufferId = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        }
        while (outputBufferId >= 0);
//        RL.i("api21 平均耗时:" + totalTime / index + "ms");
        mRecordedFrames++;
        //计算录制时间
        if (mOnRecordProgressListener != null) {
            int recordedDuration = (int) ((1000f / mFrameRate) * mRecordedFrames);
            mOnRecordProgressListener.onRecording(recordedDuration);
        }
    }
}
