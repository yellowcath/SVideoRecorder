package us.pinguo.svideo.recorder;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.encoder.MediaAudioEncoder;
import us.pinguo.svideo.encoder.MediaEncoder;
import us.pinguo.svideo.encoder.VideoMediaEncoderThread;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.RecordSemaphore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by huangwei on 2015/11/19.
 */
public class SMediaCodecRecorder extends SAbsVideoRecorder {

    protected VideoMediaEncoderThread mRecorderThread;
    protected MediaAudioEncoder mMediaAudioEncoder;

    public static volatile boolean sMuxerStarted = false;
    public static volatile boolean sMuxerStopped = false;
    public static Semaphore sStartSemaphore;
    public static Semaphore sStopSemaphore;

    protected int mFrameCount;
    /**
     * 用于等待一个视频线程，两个音频线程都完成之后在回调录音结束
     */
    protected CountDownLatch mCountDownLatch;
    protected boolean mAudioRecordFailed;

    public SMediaCodecRecorder(Context context, ICameraProxyForRecord cameraProxyForRecord) {
        super(context, cameraProxyForRecord);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected boolean initRecorder() {
        final String videoFileName = mVideoPathGenerator.generate();
        File file = new File(videoFileName);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            onVideoRecordFail(e, false);
            return false;
        }

        int previewWidth = mCameraProxyForRecord.getPreviewWidth();
        int previewHeight = mCameraProxyForRecord.getPreviewHeight();
        RL.i("previewSize:" + previewWidth + "," + previewHeight);
        sStartSemaphore = new RecordSemaphore(2);
        try {
            sStartSemaphore.acquire(2);
        } catch (InterruptedException e) {
            RL.e(e);
        }
        sMuxerStarted = false;
        sMuxerStopped = false;
        int videoRotation = mCameraProxyForRecord.getVideoRotation();
        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(videoFileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.setOrientationHint(videoRotation);
        } catch (IOException e) {
            onVideoRecordFail(e, false);
            return false;
        }

        mCountDownLatch = new CountDownLatch(3);
        mRecorderThread = new VideoMediaEncoderThread(
                previewWidth,
                previewHeight,
                mVideoBitRate,
                mFrameRate,
                mIFrameInterval,
                null,
                mediaMuxer,
                mCountDownLatch);
        mRecorderThread.setOnRecordFailListener(this);
        startRecordAudio(mediaMuxer, mCountDownLatch);
        mVideoInfo.setVideoRotation(videoRotation);
        mVideoInfo.setVideoPath(videoFileName);
        mVideoInfo.setVideoWidth(previewWidth);
        mVideoInfo.setVideoHeight(previewHeight);
        return true;
    }

    /**
     * 为了能让音视频线程都写完数据再停止，加锁
     */
    protected void lockSemaphoreForStop() {
//        if (mAudioRecordFailed) {
//            sStopSemaphore = new RecordSemaphore(1);
//            sStopSemaphore.tryAcquire(1);
//        } else {
//            sStopSemaphore = new RecordSemaphore(2);
//            sStopSemaphore.tryAcquire(2);
//        }
        sStopSemaphore = new RecordSemaphore(2);
        sStopSemaphore.tryAcquire(2);
        if (mAudioRecordFailed) {
            sStopSemaphore.release(1);
        }
    }

    @Override
    public void pauseRecord() {
        throw new UnsupportedOperationException("use SSegmentRecorder instead");
//        super.pauseRecord();
//        mMediaAudioEncoder.pauseRecord();
    }

    @Override
    protected void stopRecordNotCancel() {
        if (!isVideoRecording) {
            return;
        }
        stopRequestPreviewData();
        notifyRecordStop();
        isVideoRecording = false;
        if (mMediaAudioEncoder != null) {
            mMediaAudioEncoder.setRecordSilentAudio(true);
        }
        lockSemaphoreForStop();
        stopRecordAudio();
        mRecorderThread.finish();
        mFrameCount += mRecorderThread.getRecordedFrames();
    }

    @Override
    public void resumeRecord() {
        throw new UnsupportedOperationException("use SSegmentRecorder instead");
//        super.resumeRecord();
//        mMediaAudioEncoder.resumeRecord();
    }

    @Override
    protected void stopRecordAndCancel() {
        if (!isVideoRecording) {
            return;
        }
        //这里不回调，因为此函数由onRecordFail调用，前面已经调了onRecordFail
//        if (mOnRecordListener != null) {
//            mOnRecordListener.onRecordStop();
//        }
        stopRequestPreviewData();
        isVideoRecording = false;
        lockSemaphoreForStop();
        stopRecordAudio();
        mRecorderThread.finish();
        mFrameCount += mRecorderThread.getRecordedFrames();
    }

    protected void startRecordAudio(MediaMuxer mediaMuxer, CountDownLatch countDownLatch) {
        mMediaAudioEncoder = new MediaAudioEncoder(mediaMuxer, new MediaEncoder.MediaEncoderListener() {
            @Override
            public void onPrepared(MediaEncoder encoder) {

            }

            @Override
            public void onStopped(MediaEncoder encoder) {

            }
        }, countDownLatch);
        mMediaAudioEncoder.setOnRecordFailListener(this);
        mMediaAudioEncoder.startRecording();
    }

    protected void stopRecordAudio() {
        mMediaAudioEncoder.stopRecording();
    }

    @Override
    protected void saveVideo() {
        mThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                waitRecordFinish();
                mEncodeEndTime = System.currentTimeMillis();
                VideoInfo.fillVideoInfo(mVideoInfo.getVideoPath(),mVideoInfo);
                mFrameCount = 0;
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        RL.i("已录制视频:" + mVideoInfo.toString());
                        notifyRecordSuccess(mVideoInfo);
                    }
                });
            }
        });
    }

    protected void waitRecordFinish() {
        long s = System.currentTimeMillis();
        int qSize = -1;
        long preQSizeTime = 0;
        //第一次等1000ms
        long firstWait = 1000;
        while (true) {
            boolean timeOut = false;
            try {
                timeOut = !mCountDownLatch.await(firstWait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //不会有
            }
            firstWait = 5000;
            if (!timeOut) { //线程正常结束
                if (mVideoRecordFailed) {
                    return;
                } else {
                    break;
                }
            }
            if (!mRecorderThread.isAlive()) {
                if (mVideoRecordFailed) { //线程之前已失败
                    return;
                } else if (mRecorderThread.isSuccess()) {
                    //成功完成
                    break;
                } else {
                    long endTime = System.currentTimeMillis();
                    onVideoRecordFail(new RecordFailException("等待录制线程stop" + (endTime - s) + "ms,超时"), true);
                    return;
                }
            } else {
                if (qSize != mRecorderThread.getQueueSize()) {
                    qSize = mRecorderThread.getQueueSize();
                    preQSizeTime = System.currentTimeMillis();
                } else {
                    if (System.currentTimeMillis() - preQSizeTime > 10 * 1000) {
                        String err = (System.currentTimeMillis() - preQSizeTime) + "ms未能写完一帧,还剩" + qSize + "帧，" +
                                "可能已卡死，视为录制失败";
                        RL.e(err);
                        onVideoRecordFail(new RecordFailException(err), true);
                        return;
                    }
                }
            }
            RL.i("数据还没写完，继续等……");
        }
    }

    @Override
    public void onPreviewData(byte[] data, long timeUs) {
        if (!isVideoRecording) {
            return;
        }
        if (data != null && !mPausing) {
            mRecorderThread.addImageData(data, timeUs);
        }
    }

    @Override
    public void onVideoRecordFail(Throwable t, final boolean showToast) {
        if (mVideoRecordFailed) {
            return;
        }
        RL.i("onVideoRecordFail");
        synchronized (this) {
            if (mVideoRecordFailed) {
                return;
            }
            mVideoRecordFailed = true;
        }
        notifyRecordFail(t);
        if (t != null) {
            RL.e(Log.getStackTraceString(t));
            RL.e(t);
        }
        if (isVideoRecording) {
            RL.i("stopRecordAndCancel");
            stopRecordAndCancel();
        }
        new File(mVideoInfo.getVideoPath()).delete();
    }

    @Override
    public void onAudioRecordFail(Throwable e) {
        mAudioRecordFailed = true;
    }

    @Override
    public void startRecord() {
        mVideoRecordFailed = false;
        notifyRecordStart();
        // 开始录制
        requestPreviewData();
        mAudioRecordFailed = false;
        if (!initRecorder()) {
            return;
        }
        mRecorderThread.start();
        mEncodeStartTime = System.currentTimeMillis();
        isVideoRecording = true;
    }
}
