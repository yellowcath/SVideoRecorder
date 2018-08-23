package us.pinguo.svideoDemo.ui;

import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.ISVideoRecorder;
import us.pinguo.svideo.interfaces.OnRecordListener;
import us.pinguo.svideo.recorder.SSegmentRecorder;
import us.pinguo.svideo.utils.RL;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by huangwei on 2015/11/5.
 * 需手动根据播放进度让进度条前进，与{@link }一起使用
 */
public class SVideoTouchController implements View.OnTouchListener, OnRecordListener {
    private static final int PROGRESS_MAX = 1000;
    /**
     * 最短录制时间
     */
    public static final float MIN_RECORD_DURATION = 0 * 1000;
    /**
     * 最长录制时间
     */
    public static final float MAX_RECORD_DURATION = 30 * 1000;
    /**
     * 两次点击间隔太近则不响应
     */
    private static final float TAP_TIME_INTERVAL = 1000;

    private VideoProgressLayout mProgressBarLayout;
    private ImageView mShutterBtn;
    private ImageView mSaveBtn;
    private ImageView mDeleteBtn;

    /**
     * 长按判断时间，超出则视为长按
     */
    private long mLongPressTimeOut;

    /**
     * 用于检查长按
     */
    private Runnable mCheckLongPressRunnable;
    /**
     * 是否长按
     */
    private boolean mIsLongTouch;
    /**
     * 是否正在录制
     */
    private boolean mIsRecording;

    private long mDownTime;

    private ISVideoRecorder mSVideoRecorder;
    /**
     * 录制时间不满{@link #MIN_RECORD_DURATION}则强行录满
     */
    private boolean mForceRecordMode = false;
    /**
     * 之前的片段已录制的总时间
     */
    protected List<Integer> mSegDurations = new LinkedList<>();
    /**
     * 正在录制时用于记录录制时长
     */
    protected long mRecordingDuration;
    /**
     * 开始录制的时间
     */
    protected long mStartTime;

    protected boolean mStopRecordWhenReachMinDuration;

    private Handler mMainHandler = new Handler();

    private boolean mRecordFailed = false;

    private boolean mPausing = false;

    private boolean mSupportSubsection;

    public SVideoTouchController(VideoProgressLayout progressLayout, ImageView shutterBtn) {
        this(progressLayout, shutterBtn, null, null);
    }

    public SVideoTouchController(VideoProgressLayout progressLayout, ImageView shutterBtn, ImageView saveBtn, ImageView deleteBtn) {
        int screenWidth = progressLayout.getResources().getDisplayMetrics().widthPixels;
        int leftMargin = (int) (screenWidth * (MIN_RECORD_DURATION / MAX_RECORD_DURATION));

        mProgressBarLayout = progressLayout;
        mProgressBarLayout.setProgressMinViewLeftMargin(leftMargin);
        mLongPressTimeOut = ViewConfiguration.getLongPressTimeout();
        mShutterBtn = shutterBtn;
        mSupportSubsection = saveBtn != null;
        mSaveBtn = saveBtn;
        mDeleteBtn = deleteBtn;
        mProgressBarLayout.setMax(PROGRESS_MAX);
        mProgressBarLayout.setProgress(0);

        mCheckLongPressRunnable = new Runnable() {
            @Override
            public void run() {
                mIsLongTouch = true;
                triggerLongTouchStart();
//                PGEventBus.getInstance().post(new StartRecordVideoEvent());
            }
        };
    }

    public void setProgressBarGone() {
        mProgressBarLayout.setProgress(0);
        mProgressBarLayout.setVisibility(View.GONE);
        mShutterBtn.setPressed(false);
        mForceRecordMode = false;
    }


    private void triggerLongTouchStart() {
        if (mIsRecording && mSupportSubsection) {
            if (mSVideoRecorder != null) {
                mPausing = false;
                mSVideoRecorder.resumeRecord();
            }
            mStartTime = System.currentTimeMillis();
            mMainHandler.post(mRecordProgressRunnable);
        } else {
            mIsRecording = true;
            mPausing = false;
            mProgressBarLayout.setVisibility(View.VISIBLE);
            if (mSVideoRecorder != null) {
                mSVideoRecorder.startRecord();
            }
        }
    }

    private void triggerLongTouchEnd() {
//        mProgressBarLayout.stopProgress();
        if (mSupportSubsection) {
            if (mPausing) {
                return;
            }
            mPausing = true;
            mSegDurations.add((int) (System.currentTimeMillis() - mStartTime));
            int recordedDuration = getRecordedDuration();
            mProgressBarLayout.mVideoProgressBar.addMark(recordedDuration / (float) MAX_RECORD_DURATION);
            if (mSVideoRecorder != null) {
                mSVideoRecorder.pauseRecord();
            }
            mMainHandler.removeCallbacks(mRecordProgressRunnable);
            if (RL.isLogEnable()) {
                for (Integer i : mSegDurations) {
                    RL.i("暂时停止录制，已录制片段时长:" + i);
                }
            }
        }else{
            mProgressBarLayout.stopProgress();
            if (!mIsRecording) {
                return;
            }
            mSegDurations.add((int) (System.currentTimeMillis() - mStartTime));
            mIsRecording = false;
            if (mSVideoRecorder != null) {
                mSVideoRecorder.stopRecord();
            }
        }
    }

    public void setProgress(float progress) {
        mProgressBarLayout.setProgress(progress);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mForceRecordMode) {
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                long time = System.currentTimeMillis();
                if (time - mDownTime < TAP_TIME_INTERVAL) {
                    return false;
                }
                mDownTime = time;
                mIsLongTouch = false;
                v.postDelayed(mCheckLongPressRunnable, mLongPressTimeOut);
                return true;
//                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mRecordFailed) {
                    break;
                }
                if (mIsLongTouch) {
                    mForceRecordMode = false;
                    mForceRecordMode = !onTouchUp();
                    if (!mForceRecordMode) {
                        v.setPressed(false);
                        triggerLongTouchEnd();
                    }
//                    else {
//                        Toast.makeText(v.getContext(), R.string.video_record_time_too_short, Toast.LENGTH_SHORT).show();
//                    }
                    return true;
                } else {
                    v.removeCallbacks(mCheckLongPressRunnable);
                }
                break;
        }
        return false;
    }

    protected Runnable mRecordProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsRecording || mPausing) {
                return;
            }
            long curTime = System.currentTimeMillis();
            float progress;
            int recordedDuration = getRecordedDuration();
            mRecordingDuration = recordedDuration + (curTime - mStartTime);
            progress = mRecordingDuration / MAX_RECORD_DURATION;
            if (mStopRecordWhenReachMinDuration &&
                    recordedDuration + (curTime - mStartTime) >= SVideoTouchController.MIN_RECORD_DURATION) {
                //强行录满最短时间
                setRecordEnd();
                setProgressBarGone();
                mSVideoRecorder.stopRecord();
            } else if (progress >= 1) {
                //已经录满最长时间
                setRecordEnd();
                setProgressBarGone();
                mSVideoRecorder.stopRecord();
            } else {
                setProgress(progress);
                mMainHandler.postDelayed(this, 16);
            }
        }
    };

    public void onPause() {
        long curTime = System.currentTimeMillis();
        if (getRecordedDuration() + (curTime - mStartTime) >= SVideoTouchController.MIN_RECORD_DURATION) {
            //录制时间超过最短时间，保存
            mSVideoRecorder.stopRecord();
        } else {
            //录制时间太短，视为录制失败处理
            mSVideoRecorder.onVideoRecordFail(null, false);
        }
    }

    private int getRecordedDuration() {
        int recordedDuration = 0;
        for (int i : mSegDurations) {
            recordedDuration += i;
        }
        return recordedDuration;
    }

    /**
     * 有时候这个状态会出问题，先这样设回来
     */
    public void setForceRecordFalse() {
        mForceRecordMode = false;
    }

    public ISVideoRecorder getOnRecordListener() {
        return mSVideoRecorder;
    }

    public SVideoTouchController setSVideoRecorder(ISVideoRecorder sVideoRecorder) {
        mSVideoRecorder = sVideoRecorder;
        mSVideoRecorder.addRecordListener(this);
        return this;
    }

    public boolean onTouchUp() {
        if (mStartTime == 0) {
            mStopRecordWhenReachMinDuration = true;
            return false;
        }
        //已录制时间大于SVideoTouchController_MC.MIN_RECORD_DURATION则可以停止录制
        long curTime = System.currentTimeMillis();
        long elapsedTime = getRecordedDuration() + (curTime - mStartTime);
        boolean stopRecord = elapsedTime >= SVideoTouchController.MIN_RECORD_DURATION;
        mStopRecordWhenReachMinDuration = !stopRecord;
        return stopRecord;
    }

    public void moniTouchUp() {
        mShutterBtn.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0));
    }

    public void setRecordEnd() {
        mIsRecording = false;
    }

    @Override
    public void onRecordSuccess(VideoInfo videoInfo) {
        mSegDurations.clear();
        mProgressBarLayout.mVideoProgressBar.clearAllMarks();
        if (mSupportSubsection) {
            mSaveBtn.setVisibility(View.GONE);
            mDeleteBtn.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRecordStart() {
        mStopRecordWhenReachMinDuration = false;
        mStartTime = System.currentTimeMillis();
        mMainHandler.post(mRecordProgressRunnable);
        mRecordFailed = false;
    }

    @Override
    public void onRecordFail(Throwable t) {
        mRecordFailed = true;
        mMainHandler.removeCallbacks(mRecordProgressRunnable);
        mSegDurations.clear();
        mProgressBarLayout.mVideoProgressBar.clearAllMarks();
        setProgress(0);
        setProgressBarGone();
        setRecordEnd();
        if (mSupportSubsection) {
            mSaveBtn.setVisibility(View.GONE);
            mDeleteBtn.setVisibility(View.GONE);
        }

    }

    @Override
    public void onRecordStop() {
        mMainHandler.removeCallbacks(mRecordProgressRunnable);
        setProgress(0);
        setProgressBarGone();
    }

    @Override
    public void onRecordPause() {
        if (mSupportSubsection) {
            mSaveBtn.setVisibility(View.VISIBLE);
            mDeleteBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRecordResume() {
        if (mSupportSubsection) {
            mSaveBtn.setVisibility(View.GONE);
            mDeleteBtn.setVisibility(View.GONE);
        }
    }

    public void stopRecord() {
        if (!mIsRecording) {
            return;
        }
        mPausing = false;
        mIsRecording = false;
        mProgressBarLayout.stopProgress();
        mSVideoRecorder.stopRecord();
    }

    public void deleteLastSegment() {
        if (mSVideoRecorder instanceof SSegmentRecorder && mSegDurations.size() > 0) {
            ((SSegmentRecorder) mSVideoRecorder).deleteLastSegment();
            mProgressBarLayout.mVideoProgressBar.removeLastMark();
            mSegDurations.remove(mSegDurations.size() - 1);
            setProgress(getRecordedDuration() / (float) MAX_RECORD_DURATION);
        }
    }
}
