package us.pinguo.svideo.recorder;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.ISVideoRecorder;
import us.pinguo.svideo.interfaces.IVideoPathGenerator;
import us.pinguo.svideo.interfaces.OnRecordListener;
import us.pinguo.svideo.utils.DateVideoNameGenerator;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.SVideoUtil;
import us.pinguo.svideo.utils.SegVideoNameGenerator;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by huangwei on 2018/8/16 0016.
 */
public class SSegmentRecorder<T extends SAbsVideoRecorder> implements ISVideoRecorder {

    private T mRecorder;
    protected Vector<OnRecordListener> mOnRecordListeners = new Vector<>();
    private List<String> mVideoList = new LinkedList<>();
    private Semaphore mRecordFinished = new Semaphore(1);
    protected IVideoPathGenerator mVideoPathGenerator = new DateVideoNameGenerator();
    protected Handler mMainHandler = new Handler(Looper.getMainLooper());
    public volatile boolean isVideoRecording;
    private VideoInfo mVideoInfo = new VideoInfo();
    private Boolean mRecordFinish = false;

    public SSegmentRecorder(Context context, T recorder) {
        mRecorder = recorder;
        mRecorder.setVideoPathGenerator(new SegVideoNameGenerator(context));
        mRecorder.addRecordListener(new OnRecordListener() {
            @Override
            public void onRecordStart() {

            }

            @Override
            public void onRecordFail(Throwable t) {
                RL.e("Segment Record Fail");
                RL.e(t);
                mRecordFinished.release();
                notifyRecordFail(t);
            }

            @Override
            public void onRecordStop() {
            }

            @Override
            public void onRecordPause() {

            }

            @Override
            public void onRecordResume() {

            }

            @Override
            public void onRecordSuccess(VideoInfo videoInfo) {
                RL.i("Segment Record Success");
                mRecordFinished.release();
                mVideoList.add(videoInfo.getVideoPath());
            }
        });
    }

    @Override
    public void startRecord() {
        if (isVideoRecording) {
            return;
        }
        try {
            mRecordFinished.tryAcquire(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            clearVideoList();
            isVideoRecording = false;
            notifyRecordFail(e);
            e.printStackTrace();
            return;
        }
        isVideoRecording = true;
        mRecordFinish = false;
        mRecorder.startRecord();
        notifyRecordStart();
    }

    @Override
    public void stopRecord() {
        if (!isVideoRecording) {
            return;
        }
        isVideoRecording = false;
        notifyRecordStop();
        waitAndSaveVideo();
    }

    @Override
    public void pauseRecord() {
        mRecorder.stopRecord();
        notifyRecordPause();
    }

    @Override
    public void resumeRecord() {
        try {
            mRecordFinished.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mRecorder.startRecord();
        notifyRecordResume();
    }

    @Override
    public void cancelRecord() {
        clearVideoList();
        isVideoRecording = false;
        notifyRecordFail(new RecordCancelException("cancelRecord"));
    }

    private void clearVideoList() {
        for (String str : mVideoList) {
            new File(str).deleteOnExit();
        }
        mVideoList.clear();
    }

    private void waitAndSaveVideo() {
        //合成片段
        new Thread(new Runnable() {
            @Override
            public void run() {
                long s = System.currentTimeMillis();
                //如果最后一个片段还未制作好，需等待
                try {
                    mRecordFinished.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mRecordFinished.release();
                //开始合成
                String outPath = mVideoPathGenerator.generate();
                try {
                    SVideoUtil.combineVideoSegments(mVideoList, outPath);
                    mVideoList.clear();
                } catch (IOException e) {
                    e.printStackTrace();
                    notifyRecordFail(e);
                    mVideoList.clear();
                    return;
                }
                long e = System.currentTimeMillis();
                VideoInfo.fillVideoInfo(outPath, mVideoInfo);
                RL.i("已合成视频:" + mVideoInfo.toString() + " 合成耗时:" + (e - s) + "ms");
                synchronized (mRecordFinish) {
                    mRecordFinish = true;
                    notifyRecordSuccess(mVideoInfo);
                }
            }
        }).start();
    }

    @Override
    public void addRecordListener(OnRecordListener onRecordListener) {
        mOnRecordListeners.add(onRecordListener);
    }

    @Override
    public void removeRecordListener(OnRecordListener onRecordListener) {
        mOnRecordListeners.remove(onRecordListener);
    }

    @Override
    public boolean isRecordFailed() {
        return mRecorder.isRecordFailed();
    }

    @Override
    public void setVideoEncodingBitRate(int bitRate) {
        mRecorder.setVideoEncodingBitRate(bitRate);
    }

    @Override
    public void setVideoFrameRateAndInterval(int frameRate, int iFrameInterval) {
        mRecorder.setVideoFrameRateAndInterval(frameRate, iFrameInterval);
    }

    @Override
    public void setVideoPathGenerator(IVideoPathGenerator generator) {
        mVideoPathGenerator = generator;
    }

    /**
     * 用于主动等待录制结果，非必要
     * @param listener
     */
    public void waitRecordSuccess(OnRecordListener listener) {
        synchronized (mRecordFinish) {
            if (isRecordFailed()) {
                listener.onRecordFail(new RuntimeException("check other OnRecordListener!"));
            } else if (mRecordFinish) {
                listener.onRecordSuccess(mVideoInfo);
            } else {
                addRecordListener(listener);
            }
        }
    }

    public void deleteLastSegment() {
        if (mVideoList.size() > 0) {
            String lastSegment = mVideoList.remove(mVideoList.size() - 1);
            new File(lastSegment).delete();
        }
    }

    @Override
    public void onPreviewData(byte[] data, long timeUs) {
        mRecorder.onPreviewData(data, timeUs);
    }

    @Override
    public void onVideoRecordFail(Throwable e, boolean showToast) {
        mRecorder.onVideoRecordFail(e, showToast);
    }

    @Override
    public void onAudioRecordFail(Throwable e) {
        mRecorder.onAudioRecordFail(e);
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

}
