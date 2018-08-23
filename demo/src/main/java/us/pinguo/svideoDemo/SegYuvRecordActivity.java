package us.pinguo.svideoDemo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.interfaces.ISVideoRecorder;
import us.pinguo.svideo.interfaces.OnRecordListener;
import us.pinguo.svideo.interfaces.PreviewDataCallback;
import us.pinguo.svideo.interfaces.PreviewSurfaceListener;
import us.pinguo.svideo.interfaces.SurfaceCreatedCallback;
import us.pinguo.svideo.recorder.SMediaCodecRecorder;
import us.pinguo.svideo.recorder.SSegmentRecorder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideoDemo.ui.BottomSegMenuView;
import us.pinguo.svideoDemo.ui.IBottomMenuView;

import java.io.IOException;
import java.util.List;

public class SegYuvRecordActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback, View.OnClickListener, OnRecordListener, IBottomMenuView {
    public final static int CAMERA_FACING = Camera.CameraInfo.CAMERA_FACING_BACK;

    private Camera mCamera;

    private SSegmentRecorder mRecorder;
    private PreviewDataCallback mCallback;
    private Camera.Size mPreviewSize;
    private BottomSegMenuView mBottomMenuView;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RL.setLogEnable(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_segyuvrecord);
        mBottomMenuView = findViewById(R.id.record_bottom_layout);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceView.getHolder().addCallback(this);
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
                return CAMERA_FACING == Camera.CameraInfo.CAMERA_FACING_BACK ? 90 : 270;
            }
        };
        SMediaCodecRecorder recorder = new SMediaCodecRecorder(this, cameraProxyForRecord);
        mRecorder = new SSegmentRecorder<SMediaCodecRecorder>(getApplicationContext(), recorder);
        mRecorder.addRecordListener(this);
        mBottomMenuView.setBottomViewCallBack(this);
        mBottomMenuView.enableSVideoTouch(true);
        mBottomMenuView.enableVideoProgressLayout();
        initProgressDialog();
    }

    private void initProgressDialog() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Combining Videos...");
        mProgressDialog.setCancelable(false);//4.设置可否用back键关闭对话框
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
        Camera.CameraInfo info = new Camera.CameraInfo();
        if (mCamera != null) {
            return;
        }
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
    }


    private void startPreview(SurfaceHolder holder) {
        openCamera();
        mCamera.setDisplayOrientation(90);
        Camera.Parameters parameters = mCamera.getParameters();
        mPreviewSize = getPreviewSize();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        mCamera.addCallbackBuffer(new byte[(int) (mPreviewSize.width * mPreviewSize.height * 1.5f)]);
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.setParameters(parameters);
//        mCamera.setPreviewCallback(this);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
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
    }

    @Override
    public void onRecordSuccess(VideoInfo videoInfo) {
        mProgressDialog.dismiss();
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
        mProgressDialog.show();
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
    public void onBackPressed() {
        super.onBackPressed();
        mRecorder.cancelRecord();
    }
}
