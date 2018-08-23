package us.pinguo.svideoDemo.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideoDemo.R;


public class VideoProgressLayout extends FrameLayout {
    SegProgressBar mVideoProgressBar;
    View mVideoEndPointView;
    View mVideoProgressMinView;

    private int maxProgress;

    float ratio;

    private int minLength;

    private boolean isTransform;

    private AnimatorSet mAnimatorSet;

    public VideoProgressLayout(Context context) {
        super(context);
    }

    public VideoProgressLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoProgressLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mVideoProgressBar = findViewById(R.id.svideo_progress);
        mVideoEndPointView = findViewById(R.id.video_end_point);
        mVideoProgressMinView = findViewById(R.id.svideo_progress_min);
    }

    public void setMax(int max) {
        maxProgress = max;
        mVideoProgressBar.setMax(max);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        ratio = screenWidth * 1.0f / maxProgress;

        //开始执行动画,一闪一闪亮晶晶
        animate(mVideoEndPointView);
    }

    public void setProgress(float progress) {
        float mProgress = progress * maxProgress;
        float translationX = ratio * mProgress;
        mVideoProgressBar.setProgress((int) mProgress);
        //加4px的意思是要领先于进度条4px
        mVideoEndPointView.setTranslationX(translationX + 4);
        if (!isTransform && translationX > minLength) {
            mVideoProgressMinView.setBackgroundColor(0xFFD400);
            isTransform = true;
        }
    }

    public void setProgressMinViewLeftMargin(int leftMargin) {
        MarginLayoutParams lp = (MarginLayoutParams) mVideoProgressMinView.getLayoutParams();
        lp.leftMargin = leftMargin - lp.width / 2;
        mVideoProgressMinView.setLayoutParams(lp);
        minLength = lp.leftMargin;
    }

    public void stopProgress() {
        //把颜色变回来
        mVideoProgressMinView.setBackgroundColor(Color.WHITE);
        //状态重置
        isTransform = false;
    }

//    private void animate(final View view) {
//        Animation alphaAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.video_progress_anim);
//        view.startAnimation(alphaAnimation);
//    }

    private void animate(final View view) {
        mAnimatorSet = new AnimatorSet();
        ObjectAnimator alphaEnter = ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f);
        alphaEnter.setDuration(300);
        ObjectAnimator alphaOut = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.0f);
        alphaOut.setDuration(300);
        mAnimatorSet.play(alphaEnter).after(alphaOut);
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.clearAnimation();
                mAnimatorSet.start();
            }
        });
        mAnimatorSet.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimatorSet != null) {
            mAnimatorSet.removeAllListeners();
            mAnimatorSet.cancel();
            mAnimatorSet = null;
            RL.i("清除闪烁动画回调");
        }
        super.onDetachedFromWindow();
    }
}
