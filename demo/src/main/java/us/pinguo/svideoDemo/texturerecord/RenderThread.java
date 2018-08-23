package us.pinguo.svideoDemo.texturerecord;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import us.pinguo.svideoDemo.MyApplication;
import us.pinguo.svideoDemo.texturerecord.gles.EglCore;
import us.pinguo.svideoDemo.texturerecord.gles.EglSurface;
import us.pinguo.svideoDemo.texturerecord.gles.GLRendering;
import us.pinguo.svideoDemo.texturerecord.gles.glUtils;

public class RenderThread extends Thread implements SurfaceTexture.OnFrameAvailableListener {
    private final static String TAG = "RenderThread";
    private SurfaceHolder mSurfaceHolder;

    private RenderThreadHandler mHandler;
    private final Object mWaitReadyLock = new Object();
    private final Object mLockObject = new Object();
    boolean mReady = false;

    //rendering variables
    EglSurface mEGLSurface;
    EglCore mEGLCore;
    GLRendering mRenderer;
    int mTextureName = -1;
    private SurfaceTexture mSurfaceTexture;

    private OnSurfaceTextureUpdatedListener mSTListener = null;

    private int mCameraWidth, mCameraHeight, mCameraRotation;


    public RenderThread() {
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new RenderThreadHandler(this);

        synchronized (mWaitReadyLock) {
            mReady = true;
            mWaitReadyLock.notify();
        }

        mEGLCore = new EglCore();

        Looper.loop();

        mHandler = null;
        synchronized (mWaitReadyLock) {
            mReady = false;
        }
    }

    //waits until the looper handler is ready
    public void waitUntilHandlerReady() {
        synchronized (mWaitReadyLock) {
            while (!mReady) {
                try {
                    mWaitReadyLock.wait();
                } catch (Exception e) {

                }
            }
        }
    }

    public void shutdown() {
        Looper.myLooper().quit();
    }

    public RenderThreadHandler getHandler() {
        return mHandler;
    }

    // these calls handle the SurfaceView Surface, not the SurfaceTexture coming from the camera
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mSurfaceHolder = surfaceHolder;
        mEGLSurface = new EglSurface(mEGLCore);
        mEGLSurface.createWindowForSurface(mSurfaceHolder.getSurface());
        mEGLSurface.makeCurrent();

        mRenderer = new GLRendering();

        //we have the context, so create the SurfaceTexture now
        mTextureName = 0;
        int texture[] = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        glUtils.checkGLError("gGenTexture");
        mTextureName = texture[0];
        mSurfaceTexture = new SurfaceTexture(mTextureName);

        mSurfaceTexture.setOnFrameAvailableListener(this);
        resetSurfaceTextureToListener();
    }

    public void surfaceChanged(SurfaceHolder surfaceHolder, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        Log.d(TAG, "Viewport changed to: " + w + "x" + h);
    }

    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (mEGLSurface != null) {
            mEGLSurface.releaseEglSurface();
            mEGLSurface = null;
        }
        mEGLCore.makeNothingCurrent();
        mRenderer = null;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //setup and draw, using the last available pose if there's one

        if (mRenderer == null) {
            return;
        }
        if (surfaceTexture != mSurfaceTexture) {
            Log.i(TAG, "Unexpected surface texture");
            return;
        }

        synchronized (mLockObject) {
            surfaceTexture.updateTexImage();
        }
        displayPreview();
    }

    private void displayPreview() {
        if (mRenderer == null) {
            return;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        glUtils.checkGLError("Clear target");
        drawPreview();
        boolean swapped = mEGLSurface.swapBuffers();
        if (!swapped) {
            Log.e(TAG, "shutting down renderThread");
        }

        DisplayMetrics displayMetrics = MyApplication.getAppContext().getResources().getDisplayMetrics();
        RecordHelper.setRenderThread(this);
        RecordHelper.recordTexture(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public void drawPreview() {
        //the image appears upside down in landscape mode.
        int rotation = mCameraRotation;
        if (rotation != 90) {
            rotation = 180 - mCameraRotation;
        }
        mRenderer.drawBackground(mTextureName, rotation);
    }

    public void setOnSurfaceTextureListener(OnSurfaceTextureUpdatedListener listener) {
        mSTListener = listener;
    }

    public void resetSurfaceTextureToListener() {
        if (mSTListener != null) {
            mSTListener.onSurfaceTextureUpdated(mSurfaceTexture);
        }
    }

    public void setCameraRotation(int rotation) {
        mCameraRotation = rotation;
    }

    public void setCameraImageSize(int width, int height) {
        mCameraWidth = width;
        mCameraHeight = height;
    }

    public void enableFilter(boolean enable) {
        mRenderer.setEnableFilter(enable);
    }

    public interface OnSurfaceTextureUpdatedListener {
        void onSurfaceTextureUpdated(SurfaceTexture texture);
    }

}