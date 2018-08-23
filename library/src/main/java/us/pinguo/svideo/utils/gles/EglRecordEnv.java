package us.pinguo.svideo.utils.gles;

import android.view.Surface;

import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Created by huangwei on 2016/4/8.
 */
public class EglRecordEnv {
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;

    public EglRecordEnv(Surface surface, EGLContext sharedContext, boolean releaseSurface) {
        this(surface, sharedContext, releaseSurface, EglCore.FLAG_RECORDABLE);
    }

    public EglRecordEnv(Surface surface, EGLContext sharedContext, boolean releaseSurface, int flag) {
        mEglCore = new EglCore(sharedContext, flag);
        mInputWindowSurface = new WindowSurface(mEglCore, surface, releaseSurface);
    }

    public void makeCurrent() {
        mInputWindowSurface.makeCurrent();
    }

    public void swapBuffers() {
//        mInputWindowSurface.setPresentationTime(System.nanoTime());
        mInputWindowSurface.swapBuffers();
    }

    public void release(boolean releaseWindowSurface) {
        if (releaseWindowSurface) {
            mInputWindowSurface.release();
        }
        mEglCore.releaseNotMakeCurrent();
    }

    public void releaseAndMakeCurrent(boolean releaseWindowSurface) {
        if (releaseWindowSurface) {
            mInputWindowSurface.release();
        }
        mEglCore.release();
    }

    public EGLSurface getEGLSurface() {
        return mInputWindowSurface == null ? null : mInputWindowSurface.getEGLSurface();
    }

    public void makeCurrentReadFrom(EGLSurface mEGLSurface) {
        mInputWindowSurface.makeCurrentReadFrom(mEGLSurface);
    }
}
