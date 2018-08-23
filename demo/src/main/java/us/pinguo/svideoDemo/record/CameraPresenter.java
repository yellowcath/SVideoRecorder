package us.pinguo.svideoDemo.record;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideoDemo.mvp.Presenter;
import us.pinguo.svideoDemo.mvp.ViewController;
import us.pinguo.svideoDemo.texturerecord.RecordHelper;
import us.pinguo.svideoDemo.texturerecord.RenderThread;
import us.pinguo.svideoDemo.texturerecord.RenderThreadHandler;

import java.io.IOException;
import java.util.List;

/**
 * Created by huangwei on 2016/7/15.
 */
public class CameraPresenter implements Presenter, SurfaceHolder.Callback, RenderThread.OnSurfaceTextureUpdatedListener {

    public final static int CAMERA_FACING = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera mCamera;
    private Camera.Size mPreviewSize;
    private RenderThread mRenderThread;

    @Override
    public void attachView(ViewController controller) {
        startRenderThread();
        openCamera();
        setupPreviewSize();
    }

    @Override
    public void detachView() {
      closeCamera();
    }

    private void startRenderThread() {
        mRenderThread = new RenderThread();
        mRenderThread.setName("Rendering thread");
        mRenderThread.setCameraRotation(CAMERA_FACING == Camera.CameraInfo.CAMERA_FACING_BACK?270:90);
        mRenderThread.start();
    }

    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }

    private void openCamera() {
        if (mCamera != null) {
            return;
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int k = 0; k < Camera.getNumberOfCameras(); k++) {
            Camera.getCameraInfo(k, info);
            if (info.facing == CAMERA_FACING) {
                mCamera = Camera.open(k);
                break;
            }
        }
        if (mCamera == null) {
            throw new RuntimeException("Can't open frontal camera");
        }
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        mCamera.setParameters(parameters);
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void setupPreviewSize() {
        Camera.Parameters parameters = mCamera.getParameters();
        mPreviewSize = getProperPreviewSize(parameters);
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        RecordHelper.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mRenderThread.setCameraImageSize(mPreviewSize.width, mPreviewSize.height);
    }

    private void startPreview(SurfaceTexture texture) {
        try {
            mCamera.setPreviewTexture(texture);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //pass this surfaceHolder to the renderer
        mRenderThread.waitUntilHandlerReady();

        mRenderThread.setOnSurfaceTextureListener(this);

        RenderThreadHandler handler = mRenderThread.getHandler();
        if (handler != null) {
            handler.sendSurfaceCreated(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        RenderThreadHandler handler = mRenderThread.getHandler();
        if (handler != null) {
            handler.sendSurfaceChanged(holder, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        RenderThreadHandler handler = mRenderThread.getHandler();
        if (handler != null) {
            handler.sendSurfaceDestroyed(holder);
        }
        stopPreview();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        startPreview(texture);
    }

    private Camera.Size getProperPreviewSize(Camera.Parameters parameters) {
        int min = 640 * 480;
        int max = 720 * 1280;
        List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = null;
        for (int i = 0; i < sizeList.size(); i++) {
            Camera.Size size = sizeList.get(i);
            int value = size.width * size.height;
            if (value >= min && value <= max) {
                previewSize = size;
            }
        }
        if (previewSize == null) {
            previewSize = sizeList.get(sizeList.size() / 2);
        }
        RL.i("getProperPreviewSize:" + previewSize.width + "X" + previewSize.height);
        return previewSize;
    }

    public void enableFilter(boolean enable) {
        mRenderThread.enableFilter(enable);
    }
}
