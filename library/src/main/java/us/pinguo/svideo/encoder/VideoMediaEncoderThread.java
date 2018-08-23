package us.pinguo.svideo.encoder;

import android.media.MediaMuxer;
import us.pinguo.svideo.recorder.OnRecordFailListener;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.SVideoUtil;
import us.pinguo.svideo.utils.TimeOutThread;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by huangwei on 2015/12/15.
 * 用YUV数据录制原始视频的类
 */
public class VideoMediaEncoderThread extends TimeOutThread implements Thread.UncaughtExceptionHandler {

    protected LinkedBlockingQueue<SaveRequest> mQueue;

    protected VideoEncoderFromBuffer mRecorder;

    protected boolean mStop = false;

    protected int mRecordedFrames;
    protected int mFrameRate;
    protected OnRecordProgressListener mOnRecordProgressListener;
    protected LinkedList<byte[]> mDataObjectPool = new LinkedList<byte[]>();
    protected OnRecordFailListener mOnRecordFailListener;

    protected boolean mIsSuccess;
    protected boolean mInited;

    /**
     * 根据帧的时间戳得出
     */
    private long mDuration;
    private long mFirstFrameTime = -1;

    protected int mWidth;
    protected int mHeight;
    protected byte[] mLastFrameYUV;
    protected long mLastFrameTime;
    private String mSdkEffectKey;

    // Runs in main thread
    public VideoMediaEncoderThread(int width, int height, int bitRate, int frameRate, int iFrameInterval, String path, MediaMuxer mediaMuxer, CountDownLatch countDownLatch) {
        super(countDownLatch);
        mWidth = width;
        mHeight = height;
        setPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        setName("VideoMediaEncoderThread");
        mQueue = new LinkedBlockingQueue<SaveRequest>();
        mFrameRate = frameRate;
        initRecorder(
                width,
                height,
                bitRate,
                frameRate,
                iFrameInterval,
                path,
                mediaMuxer
        );
    }

    protected void initRecorder(int width, int height, int bitRate, int frameRate, int iFrameInterval, String path, MediaMuxer mediaMuxer) {
        if (SVideoUtil.AFTER_LOLLIPOP) {
            mRecorder = new VideoEncoderApi21(
                    width,
                    height,
                    bitRate,
                    frameRate,
                    iFrameInterval,
                    path,
                    mediaMuxer
            );
        } else {
            mRecorder = new VideoEncoderFromBuffer(
                    width,
                    height,
                    bitRate,
                    frameRate,
                    iFrameInterval,
                    mediaMuxer
            );
        }
    }

    @Override
    public void run() {
        if (mRecorder == null) {
            mCountDonwLatch.countDown();
            return;
        }
        if (!mInited) {
            Thread.currentThread().setUncaughtExceptionHandler(this);
            mInited = true;
            RL.i("+initInThread");
            mRecorder.initInThread();
            RL.i("-initInThread");
        }
        while (true) {
            SaveRequest r = null;
            try {
                RL.i("+mQueue.take");
                r = mQueue.take();
                RL.i("-mQueue.take");
                if (r.data == null) {
                    break;
                }
            } catch (InterruptedException e) {
                RL.e(e);
                continue;
            }
            RL.i("+encodeFrame");
            long s = System.currentTimeMillis();
            mRecorder.encodeFrame(r.data, r.fpsTimeUs);
            long e = System.currentTimeMillis();
            RL.i("-encodeFrame");
            synchronized (mDataObjectPool) {
                mDataObjectPool.add(r.data);
            }
            mRecordedFrames++;
            RL.i("mRecorder.encodeFrame:" + (e - s) + "ms");
            if (mFirstFrameTime < 0) {
                mFirstFrameTime = r.fpsTimeUs / 1000;
            }

            mDuration = r.fpsTimeUs / 1000 - mFirstFrameTime;
            //计算录制时间
            if (mOnRecordProgressListener != null) {
                int recordedDuration = (int) ((1000f / mFrameRate) * mRecordedFrames);
                mOnRecordProgressListener.onRecording(recordedDuration);
            }
        }
        RL.i("总帧数:" + mRecordedFrames);

        mDataObjectPool.clear();
        mQueue.clear();
        mRecorder.close();
        mIsSuccess = true;
        mCountDonwLatch.countDown();
    }

    // Runs in main thread
    public void addImageData(final byte[] data, long time) {
        if (mStop || mQueue == null) {
            return;
        }
//        L.it(TAG, " addImageData data.length = " + data.length);
        byte[] temp = null;
        if (mDataObjectPool.size() > 0) {
            synchronized (mDataObjectPool) {
                temp = mDataObjectPool.pop();
            }
        } else {
            try {
                temp = new byte[data.length];
            } catch (OutOfMemoryError e) {
                RL.e("为YUV数据包分配空间失败，丢弃");
                RL.e(e);
                return;
            }
        }
        System.arraycopy(data, 0, temp, 0, data.length);
        SaveRequest r = new SaveRequest();
        mLastFrameYUV = temp;
        mLastFrameTime = time;
        r.data = temp;
        r.fpsTimeUs = time;
        if (mQueue.size() < 100) {
            mQueue.add(r);
        }
    }

    /**
     * 根据帧的时间戳算出的，可能不准
     *
     * @return
     */
    public long getDuration() {
        return mDuration;
    }

    public boolean isSuccess() {
        return mIsSuccess;
    }

    public void finish() {
        SaveRequest r = new SaveRequest();
        r.data = null;
        r.fpsTimeUs = 0;
        mQueue.add(r);
        mStop = true;

        mLastFrameYUV = null;
        mLastFrameTime = 0;
    }

    @Deprecated
    public void forceFinish() {
        RL.i("forceFinish视频线程，还剩:" + mQueue.size() + "帧未写完");
        SaveRequest r = new SaveRequest();
        r.data = null;
        r.fpsTimeUs = 0;
        mQueue.clear();
        mQueue.add(r);
        mStop = true;
    }

    public int getRecordedFrames() {
        return mRecordedFrames + 1;//测了几个都多一帧，可能是结尾写的空帧？
    }

    public void setOnRecordProgressListener(OnRecordProgressListener onRecordProgressListener) {
        this.mOnRecordProgressListener = onRecordProgressListener;
    }

    public void setOnRecordFailListener(OnRecordFailListener onRecordFailListener) {
        this.mOnRecordFailListener = onRecordFailListener;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        mCountDonwLatch.countDown();
        throwRecordError(ex);
    }

    public int getQueueSize() {
        return mQueue == null ? 0 : mQueue.size();
    }

    static class SaveRequest {
        long fpsTimeUs;
        byte[] data;
    }

    public void throwRecordError(Throwable e) {
        if (mOnRecordFailListener != null) {
            mOnRecordFailListener.onVideoRecordFail(e, true);
        }
        RL.e(e);
        if (mRecorder != null) {
            mRecorder.close();
        }
    }
}
