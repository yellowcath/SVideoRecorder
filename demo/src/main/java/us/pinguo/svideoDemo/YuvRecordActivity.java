package us.pinguo.svideoDemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.interfaces.ISVideoRecorder;
import us.pinguo.svideo.interfaces.OnRecordListener;
import us.pinguo.svideo.interfaces.PreviewDataCallback;
import us.pinguo.svideo.interfaces.PreviewSurfaceListener;
import us.pinguo.svideo.interfaces.SurfaceCreatedCallback;
import us.pinguo.svideo.recorder.SAbsVideoRecorder;
import us.pinguo.svideo.recorder.SMediaCodecRecorder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideoDemo.ui.BottomMenuView;
import us.pinguo.svideoDemo.ui.IBottomMenuView;

import java.io.IOException;
import java.util.List;

public class YuvRecordActivity extends Activity implements Camera.PreviewCallback, View.OnClickListener, OnRecordListener, IBottomMenuView, TextureView.SurfaceTextureListener {

    private Camera mCamera;

    private SAbsVideoRecorder mRecorder;
    private PreviewDataCallback mCallback;
    private Camera.Size mPreviewSize;
    private BottomMenuView mBottomMenuView;
    private ImageView mSwitchImg;
    private TextureView mTextureView;
    public int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RL.setLogEnable(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_yuvrecord);
        mBottomMenuView = findViewById(R.id.record_bottom_layout);
        mSwitchImg = findViewById(R.id.switch_camera);
        mSwitchImg.setOnClickListener(this);
        mTextureView = findViewById(R.id.textureview);
        mTextureView.setSurfaceTextureListener(this);
        ICameraProxyForRecord cameraProxyForRecord = new ICameraProxyForRecord() {

            @Override
            public void addSurfaceDataListener(PreviewSurfaceListener listener, SurfaceCreatedCallback callback) {

            }

            @Override
            public void removeSurfaceDataListener(PreviewSurfaceListener listener) {

            }

            @Override
            public void addPreviewDataCallback(PreviewDataCallback callback) {
                mCallback = callback;
            }

            @Override
            public void removePreviewDataCallback(PreviewDataCallback callback) {
                mCallback = null;
            }

            @Override
            public int getPreviewWidth() {
                return mPreviewSize.width;
            }

            @Override
            public int getPreviewHeight() {
                return mPreviewSize.height;
            }

            @Override
            public int getVideoRotation() {
                return mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK ? 90 : 270;
            }
        };
        mRecorder = new SMediaCodecRecorder(this, cameraProxyForRecord);
        mRecorder.addRecordListener(this);
        mBottomMenuView.setBottomViewCallBack(this);
        mBottomMenuView.enableSVideoTouch(true);
        mBottomMenuView.enableVideoProgressLayout();
    }

    private Camera.Size getPreviewSize() {
        List<Camera.Size> sizeList = mCamera.getParameters().getSupportedPreviewSizes();
        for (int i = 0; i < sizeList.size(); i++) {
            Camera.Size size = sizeList.get(i);
            if (size.width == 640 || size.width == 960 || size.width == 1280) {
                return size;
            }
        }
        return sizeList.get(0);
    }

    private void openCamera() {
        if (mCamera != null) {
            return;
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int k = 0; k < Camera.getNumberOfCameras(); k++) {
            Camera.getCameraInfo(k, info);
            if (info.facing == mCameraFacing) {
                mCamera = Camera.open(k);
                break;
            }
        }
        if (mCamera == null) {
            throw new RuntimeException("Can't open frontal camera");
        }
    }


    private void startPreview(SurfaceTexture surfaceTexture) {
        openCamera();
        mCamera.setDisplayOrientation(90);
        Camera.Parameters parameters = mCamera.getParameters();
        mPreviewSize = getPreviewSize();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        List<String> focusModes = parameters.getSupportedFocusModes();
        for(String s :focusModes){
            if(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(s)){
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                break;
            }
        }
        mCamera.addCallbackBuffer(new byte[(int) (mPreviewSize.width * mPreviewSize.height * 1.5f)]);
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.setParameters(parameters);
        adjustPreviewSize();
//        mCamera.setPreviewCallback(this);
        try {
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    private void adjustPreviewSize() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mTextureView.getLayoutParams();
        params.width = displayMetrics.widthPixels;
        params.height = (int) (mPreviewSize.width / (float) mPreviewSize.height * params.width);
        mTextureView.setLayoutParams(params);
        if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mTextureView.setScaleX(-1);
        } else {
            mTextureView.setScaleX(1);
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mCallback != null) {
            long timeUs = System.nanoTime() / 1000;
            mCallback.onPreviewData(data, timeUs);
        }
        camera.addCallbackBuffer(data);
    }

    @Override
    public void onClick(View v) {
        if (v == mSwitchImg) {
            switchCamera();
        }
    }

    private void switchCamera() {
        mCameraFacing = mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK ?
                Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        openCamera();
        startPreview(mTextureView.getSurfaceTexture());
    }

    @Override
    public void onRecordSuccess(VideoInfo videoInfo) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra(PreviewActivity.FILEPATH, videoInfo.getVideoPath());
        startActivity(intent);
    }

    @Override
    public void onRecordStart() {
        Log.i("hwLog", "onRecordStart");
    }

    @Override
    public void onRecordFail(Throwable t) {
        Log.e("hwLog", Log.getStackTraceString(t));
    }

    @Override
    public void onRecordStop() {
    }

    @Override
    public void onRecordPause() {

    }

    @Override
    public void onRecordResume() {

    }

    @Override
    public ISVideoRecorder requestRecordListener() {
        return mRecorder;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBottomMenuView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBottomMenuView.onResume();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        startPreview(surface);

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
