package us.pinguo.svideo.encoder;

import android.media.MediaMuxer;
import us.pinguo.svideo.utils.RL;

import java.util.concurrent.CountDownLatch;

/**
 * Created by huangwei on 2015/12/15.
 * 用YUV数据录制原始视频的类
 */
public class VideoMediaEncoderApi21Thread extends VideoMediaEncoderThread implements Thread.UncaughtExceptionHandler {
    private VideoEncoderApi21Async mRecorder;

    private boolean mForceStop = false;

    // Runs in main thread
    public VideoMediaEncoderApi21Thread(int width, int height, int bitRate, int frameRate, int iFrameInterval,String path, MediaMuxer mediaMuxer, CountDownLatch countDownLatch) {
        super(width, height, bitRate, frameRate, iFrameInterval,path, mediaMuxer, countDownLatch);
        mRecorder.setVideoMediaEncoderThread(this);
    }

    @Override
    protected void initRecorder(int width, int height, int bitRate, int frameRate, int iFrameInterval,String path, MediaMuxer mediaMuxer) {
        mRecorder = new VideoEncoderApi21Async(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
    }

    @Override
    public void run() {
        if (mRecorder == null) {
            mCountDonwLatch.countDown();
            return;
        }
        while (true) {
            SaveRequest r = null;
            try {
                r = mQueue.take();
                if (r.data == null) {
                    break;
                }
            } catch (InterruptedException e) {
                RL.e(e);
                continue;
            }
            mRecorder.addImageData(r);
            synchronized (mDataObjectPool) {
                mDataObjectPool.add(r.data);
            }
        }
        if (!mForceStop) {
            mRecorder.waitFinish();
        }
        mDataObjectPool.clear();
        mQueue.clear();
        mRecorder.close();
        mIsSuccess = true;
        mCountDonwLatch.countDown();
    }

    @Deprecated
    @Override
    public void forceFinish() {
        super.forceFinish();
        mForceStop = true;
    }

    @Override
    public int getRecordedFrames() {
        return mRecorder.getRecordedFrames();
    }
}
