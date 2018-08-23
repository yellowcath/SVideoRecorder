package us.pinguo.svideo.recorder;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaMuxer;
import android.os.Build;
import us.pinguo.svideo.encoder.VideoSurfaceEncoderController;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.interfaces.OnSurfaceCreatedCallback;
import us.pinguo.svideo.interfaces.PreviewSurfaceListener;
import us.pinguo.svideo.interfaces.SurfaceCreatedCallback;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.RecordSemaphore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by huangwei on 2015/11/19.
 */
public class SSurfaceRecorder extends SMediaCodecRecorder implements PreviewSurfaceListener {

    public static boolean MEDIACODEC_API21_ENABLE = true;
    public static boolean MEDIACODEC_API21_ASYNC_ENABLE = false;

    protected VideoSurfaceEncoderController mRecorderThread;

    public SSurfaceRecorder(Context context, ICameraProxyForRecord cameraProxyForRecord) {
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
            onVideoRecordFail(e, true);
            return false;
        }
        mCountDownLatch = new CountDownLatch(3);
        mRecorderThread = new VideoSurfaceEncoderController(
                previewWidth,
                previewHeight,
                mVideoBitRate,
                mFrameRate,
                mIFrameInterval,
                mediaMuxer,
                mCountDownLatch,
                this);
        startRecordAudio(mediaMuxer, mCountDownLatch);
        mVideoInfo.setVideoRotation(videoRotation);
        mVideoInfo.setVideoPath(videoFileName);
        mVideoInfo.setVideoWidth(previewWidth);
        mVideoInfo.setVideoHeight(previewHeight);
        return true;
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
        mRecorderThread.awakeForQuit();
        mRecorderThread.finish();
        mFrameCount += mRecorderThread.getRecordedFrames();
    }

    @Override
    protected void stopRecordNotCancel() {
        if (!isVideoRecording) {
            return;
        }
        stopRequestPreviewData();
        isVideoRecording = false;
        if (mMediaAudioEncoder != null) {
            mMediaAudioEncoder.setRecordSilentAudio(true);
        }
        notifyRecordStop();
        lockSemaphoreForStop();
        stopRecordAudio();
        mRecorderThread.finishAndWait();
        mFrameCount += mRecorderThread.getRecordedFrames();
    }


    @Override
    public void startRecord() {
        mVideoRecordFailed = false;
        notifyRecordStart();
        if (mRecorderThread != null && mRecorderThread.isAlive()) {
            Thread.State state = mRecorderThread.getState();
            //现在不需要做分段，所以不必等上一个做完,只需要尽量让上一个线程退出就好
            if (state != null) {
                RL.i("pre mRecorderThread,state:" + state);
            }
            mRecorderThread.awakeForQuit();
        }

        // 开始录制
        mAudioRecordFailed = false;
        if (!initRecorder()) {
            return;
        }
        requestPreviewData();
        mRecorderThread.start();
        mEncodeStartTime = System.currentTimeMillis();
        isVideoRecording = true;
    }

    protected void waitRecordFinish() {
        long s = System.currentTimeMillis();
        //第一次等1000ms
        long waitTime = 1000;
        int preFrames = -1;
        while (true) {
            boolean timeOut = false;
            try {
                timeOut = !mCountDownLatch.await(waitTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //不会有
            }
            waitTime = 3000;
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
            }
            int currentFrames = mRecorderThread.getRecordedFrames();
            if (preFrames < 0) {
                preFrames = currentFrames;
            } else if (preFrames == currentFrames) {
                //3秒内一帧都没录
                long endTime = System.currentTimeMillis();
                Thread.State state = mRecorderThread.getState();
                String err = "等待录制线程stop" + (endTime - s) + "ms,已录制:" + preFrames + "帧，未变化,state:" + (state == null ? "null" : state) + ",视为卡死";
                onVideoRecordFail(new RecordFailException(err), true);
                return;
            }
            RL.i("数据还没写完，继续等……，已录制:" + mRecorderThread.getRecordedFrames());
        }
    }

    @Override
    protected void requestPreviewData() {
        if (mCameraProxyForRecord != null) {
            mCameraProxyForRecord.addSurfaceDataListener(this, new SurfaceCreatedCallback() {
                @Override
                public void setSurfaceCreateCallback(OnSurfaceCreatedCallback createCallback) {
                    mRecorderThread.setOnSurfaceCreatedCallback(createCallback);
                }
            });
        }
    }

    @Override
    protected void stopRequestPreviewData() {
        if (mCameraProxyForRecord != null) {
            mCameraProxyForRecord.removeSurfaceDataListener(this);
        }
    }


    @Override
    public void onFrameAvaibleSoon() {
        if (mRecorderThread != null) {
            if (!mPausing) {
                mRecorderThread.frameAvailableSoon();
            }
        }
    }
}
