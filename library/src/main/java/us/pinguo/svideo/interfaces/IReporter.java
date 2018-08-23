package us.pinguo.svideo.interfaces;

import android.content.Context;

/**
 * Created by huangwei on 2016/1/22.
 */
public interface IReporter {
    void reportError(Throwable t);

    void reportEvent_SOURCE_VIDEO_ENCODE_FPS(Context context, int duration);

    void reportEvent_SOURCE_VIDEO_ENCODE_FPS_API18(Context context, int duration);
}
