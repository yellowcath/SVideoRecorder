package us.pinguo.svideoDemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import us.pinguo.svideo.bean.VideoInfo;
import us.pinguo.svideo.interfaces.ICameraProxyForRecord;
import us.pinguo.svideo.interfaces.ISVideoRecorder;
import us.pinguo.svideo.interfaces.OnRecordListener;
import us.pinguo.svideo.interfaces.PreviewDataCallback;
import us.pinguo.svideo.interfaces.PreviewSurfaceListener;
import us.pinguo.svideo.interfaces.SurfaceCreatedCallback;
import us.pinguo.svideo.recorder.SSegmentRecorder;
import us.pinguo.svideo.recorder.SSurfaceRecorder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideoDemo.record.CameraPresenter;
import us.pinguo.svideoDemo.record.IRecordView;
import us.pinguo.svideoDemo.texturerecord.RecordHelper;
import us.pinguo.svideoDemo.ui.BottomSegMenuView;
import us.pinguo.svideoDemo.ui.IBottomMenuView;

/**
 * Created by huangwei on 2016/1/25.
 */
public class TextureRecordActivity extends Activity implements IRecordView, View.OnClickListener, OnRecordListener, IBottomMenuView {

    private SurfaceView mSurfaceView;
    static SSegmentRecorder mRecorder;
    private CameraPresenter mCameraPresenter;
    private BottomSegMenuView mBottomMenuView;
    private ImageView mFilterImg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_texture_record);

        mCameraPresenter = new CameraPresenter();
        mCameraPresenter.attachView(this);

        mFilterImg = findViewById(R.id.movie_filter);
        mFilterImg.setOnClickListener(this);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        mSurfaceView.getHolder().addCallback(mCameraPresenter);

        SSurfaceRecorder recorder = initVideoRecorder();
        mRecorder = new SSegmentRecorder<SSurfaceRecorder>(getApplicationContext(), recorder);
        mRecorder.addRecordListener(this);

        mBottomMenuView = findViewById(R.id.record_bottom_layout);
        mBottomMenuView.setBottomViewCallBack(this);
        mBottomMenuView.enableSVideoTouch(true);
        mBottomMenuView.enableVideoProgressLayout();
    }

    private SSurfaceRecorder initVideoRecorder() {
        SSurfaceRecorder.MEDIACODEC_API21_ENABLE = false;
        SSurfaceRecorder.MEDIACODEC_API21_ASYNC_ENABLE = true;
        ICameraProxyForRecord cameraProxyForRecord = new ICameraProxyForRecord() {


            @Override
            public void addSurfaceDataListener(PreviewSurfaceListener previewSurfaceListener, SurfaceCreatedCallback surfaceCreatedCallback) {
                RecordHelper.setPreviewSurfaceListener(previewSurfaceListener, surfaceCreatedCallback);
            }

            @Override
            public void removeSurfaceDataListener(PreviewSurfaceListener previewSurfaceListener) {
                RecordHelper.setPreviewSurfaceListener(null, null);
            }

            @Override
            public void addPreviewDataCallback(PreviewDataCallback callback) {
                RecordHelper.setPreviewDataCallback(callback);
            }

            @Override
            public void removePreviewDataCallback(PreviewDataCallback callback) {
                RecordHelper.setPreviewDataCallback(null);
            }

            @Override
            public int getVideoRotation() {
                return 0;
            }

            @Override
            public int getPreviewWidth() {
                return mCameraPresenter.getPreviewSize().height;
            }

            @Override
            public int getPreviewHeight() {
                return mCameraPresenter.getPreviewSize().width;
            }
        };
        return new SSurfaceRecorder(getApplicationContext(), cameraProxyForRecord);
    }

    @Override
    public void onClick(View v) {
        if (v == mFilterImg) {
            mFilterImg.setSelected(!mFilterImg.isSelected());
            mFilterImg.setColorFilter(mFilterImg.isSelected() ? 0xFFFF0000 : 0xFFFFFFFF);
            mCameraPresenter.enableFilter(mFilterImg.isSelected());
        }
    }

    @Override
    public void onRecordSuccess(VideoInfo videoInfo) {

    }

    @Override
    public void onRecordStart() {

    }

    @Override
    public void onRecordFail(Throwable t) {

    }

    @Override
    public void onRecordStop() {
        Intent intent = new Intent(this, PreviewActivity2.class);
//        intent.putExtra(PreviewActivity.FILEPATH, videoInfo.getVideoPath());
        startActivity(intent);
    }

    @Override
    public void onRecordPause() {
        RL.i("onRecordPause");
    }

    @Override
    public void onRecordResume() {
        RL.i("onRecordResume");
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
    protected void onDestroy() {
        super.onDestroy();
        mCameraPresenter.detachView();
        mCameraPresenter = null;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mRecorder.cancelRecord();
    }
}
