package us.pinguo.svideo.interfaces;

import us.pinguo.svideo.bean.VideoInfo;

/**
 * Created by huangwei on 2016/1/21.
 */
public interface OnRecordListener {
    void onRecordSuccess(VideoInfo videoInfo);

    void onRecordStart();

    void onRecordFail(Throwable t);

    void onRecordStop();

    void onRecordPause();

    void onRecordResume();
}
