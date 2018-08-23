package us.pinguo.svideoDemo.texturerecord.gles;

// Utility functions to manage an EGLSurface setup. Some bits of code are from Google's Grafika project,
// released under Apache License v2.0.


import android.util.Log;
import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLSurface;

public class EglSurface {
    private static final String TAG = "EglSurface";

    private EglCore mEglCore = null;
    private EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;

    public EglSurface(EglCore eglCore) {
        mEglCore = eglCore;
    }

    public void createWindowForSurface(Surface surface) {
        if (mEGLSurface != EGL10.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGLSurface already created");
        }
        mEGLSurface = mEglCore.createWindowSurface(surface);
    }

    /**
     * Release the EGL surface.
     */
    public void releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface);
        mEGLSurface = EGL10.EGL_NO_SURFACE;
    }

    public void makeCurrent() {
        mEglCore.makeCurrent(mEGLSurface);
    }

    public boolean swapBuffers() {
        boolean result = mEglCore.swapBuffers(mEGLSurface);
        if (!result) {
            Log.d(TAG, "swapBuffers() failed");
        }
        return result;
    }
}
