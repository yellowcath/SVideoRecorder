package us.pinguo.svideoDemo.texturerecord;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;
import android.view.Surface;
import us.pinguo.svideo.interfaces.OnSurfaceCreatedCallback;
import us.pinguo.svideo.interfaces.PreviewDataCallback;
import us.pinguo.svideo.interfaces.PreviewSurfaceListener;
import us.pinguo.svideo.interfaces.SurfaceCreatedCallback;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.SVideoUtil;
import us.pinguo.svideo.utils.gles.EglCore;
import us.pinguo.svideo.utils.gles.EglRecordEnv;
import us.pinguo.svideo.utils.gles.GlUtil;
import us.pinguo.svideoDemo.MyApplication;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by huangwei on 2016/4/8.
 */
public class RecordHelper {

    public static PreviewDataCallback sDataCallback;
    public static PreviewSurfaceListener sSurfaceListener;
    public static volatile Surface sSurface;
    private static EglRecordEnv mEGLEnv;
    private static int mPreviewWidth, mPreviewHeight;
    /**
     * 是否使用BlitFramebuffer模式
     */
    private static boolean useGlBlitFramebuffer = true;

    private static EGL10 mEGL;
    private static EGLDisplay mEGLDisplay;
    private static EGLSurface mEGLSurface;
    private static EGLContext mEGLContext;
    /**
     * 录第一帧的时间
     */
    private static boolean sStopRecordSurface;
    private static RenderThread sRenderThread;

    public static void setPreviewSize(int previewWidth, int previewHeight) {
        mPreviewHeight = previewHeight;
        mPreviewWidth = previewWidth;
    }

    public static void setPreviewDataCallback(PreviewDataCallback callback) {
        synchronized (RecordHelper.class) {
            sDataCallback = callback;
        }
    }

    public static synchronized void setPreviewSurfaceListener(PreviewSurfaceListener listener, SurfaceCreatedCallback createdCallback) {
        RL.i("setPreviewSurfaceListener+:" + listener);
        if (listener == null) {
            sStopRecordSurface = true;
            sSurfaceListener = null;
            RL.i("setPreviewSurfaceListener return");
            if (mEGLEnv != null) {
                releaseEGLEnvInOtherThread();
            }
            return;
        } else {
            sStopRecordSurface = false;
        }
        sSurfaceListener = listener;
        if (createdCallback != null) {
            createdCallback.setSurfaceCreateCallback(new OnSurfaceCreatedCallback() {
                @Override
                public void onSurfaceCreate(Surface surface) {
                    sSurface = surface;
                }
            });
        }
        RL.i("setPreviewSurfaceListener-");
    }

    public static void createEGLEnv(Surface surface) {
        if (mEGL == null) {
            mEGL = (EGL10) EGLContext.getEGL();
        }
        mEGLDisplay = mEGL.eglGetCurrentDisplay();
        mEGLContext = mEGL.eglGetCurrentContext();
        mEGLSurface = mEGL.eglGetCurrentSurface(EGL10.EGL_DRAW);
        try {
            mEGLEnv = new EglRecordEnv(surface, mEGLContext, false, EglCore.FLAG_RECORDABLE);
        } catch (Exception e) {
            RL.e(e);
            mEGLEnv = null;
        }
    }

    public static void releaseEGLEnv() {
        if (mEGLEnv != null) {
            mEGLEnv.releaseAndMakeCurrent(true);
            mEGLEnv = null;
            if (mEGL != null) {
                mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
            }
        }
        mEGL = null;
        mEGLContext = null;
        mEGLSurface = null;
        mEGLDisplay = null;
        sSurface = null;
    }

    public static void releaseEGLEnvInOtherThread() {
        if (mEGLEnv != null) {
            mEGLEnv.release(true);
            mEGLEnv = null;
        }
        mEGL = null;
        mEGLContext = null;
        mEGLSurface = null;
        mEGLDisplay = null;
        sSurface = null;
    }

    public static void recordTexture(int surfaceWidth, int surfaceHeight) {
        drawToSurface(surfaceWidth, surfaceHeight);
    }


    private static synchronized void drawToSurface(int surfaceWidth, int surfaceHeight) {
        if (sStopRecordSurface && sSurfaceListener != null) {
            releaseEGLEnv();
            sSurfaceListener.onFrameAvaibleSoon();
            RecordHelper.sSurfaceListener = null;
            RecordHelper.sSurface = null;
        }
        if (RecordHelper.sSurfaceListener == null || RecordHelper.sSurface == null) {
            return;
        }
        if (mEGLEnv == null) {
            if (GlUtil.supportGL3(MyApplication.getAppContext())) {
                useGlBlitFramebuffer = true;
            } else {
                useGlBlitFramebuffer = false;
            }
            RL.i("useGlBlitFramebuffer:" + useGlBlitFramebuffer);
            if (RecordHelper.sSurface == null) {
                RL.i("surface还未创建好，return");
                return;
            }
            createEGLEnv(RecordHelper.sSurface);
        }
        if (mEGLEnv == null || mEGL == null) {
            //EGL环境创建失败
            return;
        }
        if (RecordHelper.sSurfaceListener != null) {
            RecordHelper.sSurfaceListener.onFrameAvaibleSoon();
        }
        long ss = System.currentTimeMillis();
        int recordWidth = mPreviewHeight;
        int recordHeight = surfaceHeight == surfaceWidth ? mPreviewHeight : mPreviewWidth;
        if (useGlBlitFramebuffer) {
            drawBlitFrameBuffer(recordWidth, recordHeight, surfaceWidth, surfaceHeight);
        } else {
            drawBlit2X(recordWidth, recordHeight, surfaceWidth, surfaceHeight);
        }
        long nSec = System.nanoTime();
        SVideoUtil.setPresentationTime(nSec);
        mEGLEnv.swapBuffers();
        boolean rtn = mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        RL.it("surfaceRecord", "record surface,useGlBlitFramebuffer:" + useGlBlitFramebuffer + "" +
                " record:" + recordWidth + "X" + recordHeight + " surface:" + surfaceWidth + "X" + surfaceHeight);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static void drawBlitFrameBuffer(int recordWidth, int recordHeight, int surfaceWidth, int surfaceHeight) {
        GLES20.glFinish();
        mEGLEnv.makeCurrentReadFrom(mEGLSurface);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES30.glBlitFramebuffer(
                0, 0, surfaceWidth, surfaceHeight,
                0, 0, recordWidth, recordHeight,
                GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
        int err;
        if ((err = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
            String errStr = "ERROR: glBlitFramebuffer failed: 0x" +
                    Integer.toHexString(err);
            RL.w(errStr);
            useGlBlitFramebuffer = false;
            drawBlit2X(recordWidth, recordHeight, surfaceWidth, surfaceHeight);
        }
    }

    private static void drawBlit2X(int recordWidth, int recordHeight, int surfaceWidth, int surfaceHeight) {
        GLES20.glFinish();
        mEGLEnv.makeCurrent();
        if (sRenderThread != null) {
            sRenderThread.drawPreview();
        }
    }

    public static void setRenderThread(RenderThread renderThread) {
        sRenderThread = renderThread;
    }


    /**
     * Saves the EGL surface to a file.
     * <p/>
     * Expects that this object's EGL surface is current.
     */
    public Bitmap saveFrameToBitmap(int width, int height) {
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        GlUtil.checkGlError("glReadPixels");
        buf.rewind();

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buf);
        return bmp;
    }

    public static Bitmap yToBitmap(byte[] yuv, int width, int height) {
        if (yuv == null) {
            return null;
        }
        int[] colors = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            byte y = yuv[i];
            colors[i] = Color.argb(255, y, y, y);
        }
        Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
        return bitmap;
    }

    private static class NotSupportGlBlitFramebufferException extends Throwable {
        public NotSupportGlBlitFramebufferException(String detailMessage) {
            super(detailMessage);
        }
    }
}
