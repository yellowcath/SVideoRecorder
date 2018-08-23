package us.pinguo.svideo.interfaces;

import us.pinguo.svideo.recorder.OnRecordFailListener;

/**
 * Created by huangwei on 2016/1/21
 */
public interface ISVideoRecorder extends PreviewDataCallback, OnRecordFailListener {
    void startRecord();

    void stopRecord();

    void pauseRecord();

    void resumeRecord();

    void cancelRecord();

    void addRecordListener(OnRecordListener onRecordListener);

    void removeRecordListener(OnRecordListener onRecordListener);

    boolean isRecordFailed();

    void setVideoEncodingBitRate(int bitRate);

    /**
     * @param frameRate {@link android.media.MediaFormat#KEY_FRAME_RATE}
     * @param iFrameInterval {@link android.media.MediaFormat#KEY_I_FRAME_INTERVAL}
     */
    void setVideoFrameRateAndInterval(int frameRate, int iFrameInterval);

    /**
     * 开始录新视频时会调用generator生成视频路径
     */
    void setVideoPathGenerator(IVideoPathGenerator generator);
}
