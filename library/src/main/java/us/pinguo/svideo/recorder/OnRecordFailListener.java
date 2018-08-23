package us.pinguo.svideo.recorder;

/**
 * Created by huangwei on 2015/12/26.
 */
public interface OnRecordFailListener {
    void onVideoRecordFail(Throwable e, final boolean showToast);

    void onAudioRecordFail(Throwable e);
}
