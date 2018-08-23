package us.pinguo.svideo.interfaces;

/**
 * Created by huangwei on 2016/1/22.
 */
public interface ICameraProxyForRecord {
    void addSurfaceDataListener(PreviewSurfaceListener listener,SurfaceCreatedCallback callback);

    void removeSurfaceDataListener(PreviewSurfaceListener listener);

    void addPreviewDataCallback(PreviewDataCallback callback);

    void removePreviewDataCallback(PreviewDataCallback callback);

    /**
     * @return 视频需要旋转的角度
     */
    int getVideoRotation();

    int getPreviewWidth();

    int getPreviewHeight();
}
