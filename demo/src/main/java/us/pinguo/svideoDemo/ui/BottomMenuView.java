package us.pinguo.svideoDemo.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import us.pinguo.svideoDemo.R;

/**
 * Created by huangwei on 2016/7/15.
 */
public class BottomMenuView extends RelativeLayout implements View.OnClickListener {

    private boolean mEnableSVideoTouch = false;
    private VideoProgressLayout mSVideoProgressBar;

    private SVideoTouchController mSVideoTouchListener;
    private IBottomMenuView mBottomViewCallBack;
    private ImageView mShutterBtn;
    private ImageView mSaveBtn;

    public BottomMenuView(Context context) {
        super(context);
    }

    public BottomMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mShutterBtn = (ImageView) findViewById(R.id.shutter_btn);
        mSVideoProgressBar = (VideoProgressLayout) findViewById(R.id.video_progress_layout);
        mSaveBtn = findViewById(R.id.video_save);
        mSaveBtn.setVisibility(View.GONE);
        mSaveBtn.setOnClickListener(this);
    }
    /**
     * 设置底部bar回调
     *
     * @param callback
     */
    public void setBottomViewCallBack(IBottomMenuView callback) {
        mBottomViewCallBack = callback;
        if (mBottomViewCallBack != null && mSVideoTouchListener != null) {
            mSVideoTouchListener.setSVideoRecorder(mBottomViewCallBack.requestRecordListener());
        }
    }

    public void enableVideoProgressLayout() {
        mSVideoProgressBar.setVisibility(VISIBLE);
    }

    public void enableSVideoTouch(boolean enable) {
        if (mEnableSVideoTouch == enable) {
            return;
        }
        mEnableSVideoTouch = enable;
        if (enable) {
            if (mSVideoProgressBar != null && mEnableSVideoTouch && mSVideoTouchListener == null) {
                mSVideoTouchListener = new SVideoTouchController(mSVideoProgressBar, mShutterBtn,null,null);
                if (mBottomViewCallBack != null) {
                    mSVideoTouchListener.setSVideoRecorder(mBottomViewCallBack.requestRecordListener());
                }
                mShutterBtn.setOnTouchListener(mSVideoTouchListener);
            }
        } else {
            mSVideoTouchListener = null;
            mShutterBtn.setOnTouchListener(null);
        }
    }

    public void onResume() {
        if (mSVideoTouchListener != null) {
            mSVideoTouchListener.setForceRecordFalse();
        }
    }

    public void onPause() {
        if (mSVideoTouchListener != null) {
            mSVideoTouchListener.onPause();
        }
    }

    @Override
    public void onClick(View v) {
        if(v== mSaveBtn){
            mSVideoTouchListener.stopRecord();
            mSaveBtn.setVisibility(View.GONE);
        }
    }
}
