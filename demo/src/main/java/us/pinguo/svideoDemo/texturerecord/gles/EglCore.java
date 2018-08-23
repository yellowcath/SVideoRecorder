package us.pinguo.svideoDemo.texturerecord.gles;

// Utility functions to manage an EGL setup. Some bits of code are from Google's Grafika project,
// released under Apache License v2.0.

import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;


public class EglCore {
    private static final String TAG = "EGL core";

    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;

    private EGL10 mEGL;

    public EglCore() {
        this(null);
    }

    public EglCore(EGLContext sharedContext) {
        if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up");
        }

        mEGL = (EGL10)EGLContext.getEGL();

        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("Error getting EGLDisplay");
        }

        if(!mEGL.eglInitialize(mEGLDisplay, null)) {
            mEGLDisplay = null;
            throw new RuntimeException(("EGL failed to initialise"));
        }

        if (mEGLContext == EGL10.EGL_NO_CONTEXT) {
            int[] attribList = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_NONE,
            };

            EGLConfig[] eglConfigs = new EGLConfig[1];
            int[] configCount = new int[1];
            if (!mEGL.eglChooseConfig(mEGLDisplay, attribList, eglConfigs, eglConfigs.length,
                    configCount)) {
                throw new RuntimeException("Error choosing EGL configuration");
            }
            int[] attribCtxt = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            mEGLContext = mEGL.eglCreateContext(mEGLDisplay, eglConfigs[0], EGL10.EGL_NO_CONTEXT, attribCtxt);
            mEGLConfig = eglConfigs[0];
            checkEGLError("Creating context");
        }
    }

    public void release() {
        if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
            mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT);
            mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEGL.eglTerminate(mEGLDisplay);

            mEGLDisplay = EGL10.EGL_NO_DISPLAY;
            mEGLContext = EGL10.EGL_NO_CONTEXT;
            mEGLConfig = null;
        }
    }

    public EGLSurface createWindowSurface(Surface surface) {
        // Create a window surface, and attach it to the Surface we received.
        int[] surfaceAttribs = {
                EGL10.EGL_NONE
        };
        EGLSurface eglSurface = mEGL.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface,
                surfaceAttribs);
        checkEGLError("eglCreateWindowSurface for Surface");
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }
        return eglSurface;
    }
    public void releaseSurface(EGLSurface eglSurface) {
        mEGL.eglDestroySurface(mEGLDisplay, eglSurface);
    }

    public void makeCurrent(EGLSurface eglSurface) {
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            throw new RuntimeException("surface to be made current without a display");
        }
        if (!mEGL.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    public void makeNothingCurrent() {
        if (!mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    public boolean swapBuffers(EGLSurface eglSurface) {
        return mEGL.eglSwapBuffers(mEGLDisplay, eglSurface);
    }

    private void checkEGLError(String msg) {
        int err;
        if ((err = mEGL.eglGetError()) != EGL10.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL Error: 0x"+ Integer.toHexString(err));
        }
    }
}
