package us.pinguo.svideoDemo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by huangwei on 2018/8/17 0017.
 */
public class SegProgressBar extends ProgressBar {

    private List<Float> mMarkList = new LinkedList<>();
    private int mMarkColor;
    private int mMarkWidth;
    private Paint mPaint = new Paint();

    public SegProgressBar(Context context) {
        super(context);
    }

    public SegProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SegProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMarkColor = 0xFFFFFFFF;
        mMarkWidth = (int) (getResources().getDisplayMetrics().density * 2);
        mPaint.setColor(mMarkColor);
        mPaint.setStyle(Paint.Style.FILL);
    }

    public void addMark(float markProgress) {
        mMarkList.add(markProgress);
        invalidate();
    }

    public void removeLastMark() {
        if (mMarkList.size() > 0) {
            mMarkList.remove(mMarkList.size() - 1);
            invalidate();
        }
    }

    public void setMarkColor(int markColor) {
        mMarkColor = markColor;
        mPaint.setColor(mMarkColor);
    }

    public void setMarkWidth(int markWidth) {
        mMarkWidth = markWidth;
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int left = getPaddingLeft();
        int right = getWidth() - getPaddingRight();
        int top = getPaddingTop();
        int bottom = getHeight() - getPaddingBottom();

        for (float progress : mMarkList) {
            int markPos = (int) (left + (right - left) * progress);
            canvas.drawRect(markPos - mMarkWidth / 2, top, markPos + mMarkWidth / 2, bottom, mPaint);
        }
    }

    public void clearAllMarks() {
        mMarkList.clear();
        invalidate();
    }
}
