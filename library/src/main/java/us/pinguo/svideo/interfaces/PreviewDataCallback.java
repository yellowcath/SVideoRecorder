package us.pinguo.svideo.interfaces;

/**
 * Created by huangwei on 2016/1/21.
 */
public interface PreviewDataCallback {
    /**
     * @param data
     * @param timeUs 单位us(例如:System.nanoTime()/1000)
     */
    void onPreviewData(byte[] data, long timeUs);
}
