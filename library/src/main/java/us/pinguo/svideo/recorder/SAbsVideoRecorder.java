package us.pinguo.svideo.recorder;

import android.content.Context;
import android.os.Handler;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.interfaces.ISVideoRecorder;
import us.pinguo.svideo.interfaces.IVideoPathGenerator;
import us.pinguo.svideo.interfaces.OnRecordListener;
import us.pinguo.svideo.utils.DateVideoNameGenerator;

import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by huangwei on 2015/12/15.
 */
public abstract class SAbsVideoRecorder implements ISVideoRecorder {

    public static final int DEFAULT_FRAME_RATE = 24;

    public static final int DEFAULT_I_FRAME_INTERVAL = 1;
    /**
     * 视频输出码率
     */
    public static final int DEFAULT_VIDEO_BIT_RATE = 1500000;
    public volatile boolean isVideoRecording;

    protected Context mContext;
    protected ICameraProxyForRecord mCameraProxyForRecord;

    protected Vector<OnRecordListener> mOnRecordListeners = new Vector<>();

    protected volatile boolean mPausing = false;

    protected Handler mMainHandler;

    protected ExecutorService mThreadPool = Executors.newSingleThreadExecutor();

    protected long mEncodeStartTime, mEncodeEndTime;

    protected VideoInfo mVideoInfo = new VideoInfo();

    protected volatile boolean mVideoRecordFailed = false;
    /**
     * {@link android.media.MediaFormat#KEY_FRAME_RATE}
     */
    protected int mFrameRate = DEFAULT_FRAME_RATE;
    /**
     * {@link android.media.MediaFormat#KEY_I_FRAME_INTERVAL}
     */
    protected int mIFrameInterval = DEFAULT_I_FRAME_INTERVAL;

    protected int mVideoBitRate = DEFAULT_VIDEO_BIT_RATE;

    /**
     * 录新视频时会用于生成视频路径
     */
    protected IVideoPathGenerator mVideoPathGenerator = new DateVideoNameGenerator();

    public SAbsVideoRecorder(Context context, ICameraProxyForRecord cameraProxyForRecord) {
        mContext = context;
        mCameraProxyForRecord = cameraProxyForRecord;
        mMainHandler = new Handler(context.getMainLooper());
    }

    protected abstract boolean initRecorder();

    protected abstract void stopRecordNotCancel();

    protected abstract void stopRecordAndCancel();

    protected abstract void saveVideo();

    @Override
    public void setVideoPathGenerator(IVideoPathGenerator generator) {
        if (generator != null) {
            mVideoPathGenerator = generator;
        }
    }

    @Override
    public void stopRecord() {
        if (!isVideoRecording || mVideoRecordFailed) {
            return;
        }
        stopRecordNotCancel();
        saveVideo();
    }

    @Override
    public void cancelRecord() {
        if (!isVideoRecording || mVideoRecordFailed) {
            return;
        }
        stopRecordAndCancel();
        notifyRecordFail(new RecordCancelException("cancelRecord"));
    }

    @Override
    public void pauseRecord() {
        mPausing = true;
        notifyRecordPause();
    }

    @Override
    public void resumeRecord() {
        mPausing = false;
        notifyRecordResume();
    }

    @Override
    public void addRecordListener(OnRecordListener onRecordListener) {
        if (!mOnRecordListeners.contains(onRecordListener)) {
            mOnRecordListeners.add(onRecordListener);
        }
    }

    @Override
    public void removeRecordListener(OnRecordListener onRecordListener) {
        mOnRecordListeners.remove(onRecordListener);
    }

    protected void notifyRecordSuccess(final VideoInfo videoInfo) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mOnRecordListeners.size(); i++) {
                    OnRecordListener listener = mOnRecordListeners.get(i);
                    if (listener != null) {
                        listener.onRecordSuccess(videoInfo);
                    }
                }
            }
        });
    }

    protected void notifyRecordStop() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mOnRecordListeners.size(); i++) {
                    OnRecordListener listener = mOnRecordListeners.get(i);
                    if (listener != null) {
                        listener.onRecordStop();
                    }
                }
            }
        });
    }

    protected void notifyRecordFail(final Throwable t) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mOnRecordListeners.size(); i++) {
                    OnRecordListener listener = mOnRecordListeners.get(i);
                    if (listener != null) {
                        listener.onRecordFail(t);
                    }
                }
            }
        });
    }

    protected void notifyRecordStart() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mOnRecordListeners.size(); i++) {
                    OnRecordListener listener = mOnRecordListeners.get(i);
                    if (listener != null) {
                        listener.onRecordStart();
                    }
                }
            }
        });
    }

    protected void notifyRecordPause() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mOnRecordListeners.size(); i++) {
                    OnRecordListener listener = mOnRecordListeners.get(i);
                    if (listener != null) {
                        listener.onRecordPause();
                    }
                }
            }
        });
    }

    protected void notifyRecordResume() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mOnRecordListeners.size(); i++) {
                    OnRecordListener listener = mOnRecordListeners.get(i);
                    if (listener != null) {
                        listener.onRecordResume();
                    }
                }
            }
        });
    }

    @Override
    public void setVideoEncodingBitRate(int bitRate) {
        if (bitRate <= 0) {
            throw new IllegalArgumentException("Video encoding bit rate is not positive");
        }
        mVideoBitRate = bitRate;
    }

    @Override
    public void setVideoFrameRateAndInterval(int frameRate, int iFrameInterval) {
        mFrameRate = frameRate;
        mIFrameInterval = iFrameInterval;
    }

    /**
     * 获取原始预览数据
     */
    protected void requestPreviewData() {
        if (mCameraProxyForRecord != null) {
            mCameraProxyForRecord.addPreviewDataCallback(this);
        }
    }

    protected void stopRequestPreviewData() {
        if (mCameraProxyForRecord != null) {
            mCameraProxyForRecord.removePreviewDataCallback(this);
        }
    }

    @Override
    public boolean isRecordFailed() {
        return mVideoRecordFailed;
    }
}
