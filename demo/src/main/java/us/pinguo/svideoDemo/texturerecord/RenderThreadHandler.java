package us.pinguo.svideoDemo.texturerecord;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;

public class RenderThreadHandler extends Handler {
    final static private String TAG = "RenderThreadHandler";
    private final int SET_SURFACE_TEXTURE_TO_LISTENER_MSG = 0;
    private final int SHUTDOWN_MSG = 2;

    private final int CAMERA_SIZE_MSG = 5;
    private final int CAMERA_ROTATION = 6;

    private final int SURFACE_CREATED_MSG = 10;
    private final int SURFACE_CHANGED_MSG = 11;
    private final int SURFACE_DESTROYED_MSG = 12;


    private WeakReference<RenderThread> mRenderThreadWeakReference;
    public RenderThreadHandler(RenderThread thread) {
        mRenderThreadWeakReference = new WeakReference<RenderThread>(thread);
    }

    public void sendCameraImageSize(int wid, int hei) {
        sendMessage(obtainMessage(CAMERA_SIZE_MSG, wid, hei));
    }
    public void sendCameraRotation(int rotation) {
        sendMessage(obtainMessage(CAMERA_ROTATION, rotation, 0));
    }

    //messages that get called from the main thread
    public void sendSurfaceCreated(SurfaceHolder holder) {
        sendMessage(obtainMessage(SURFACE_CREATED_MSG, holder));
    }
    public void sendSurfaceChanged(SurfaceHolder holder, int wid, int hei) {
        sendMessage(obtainMessage(SURFACE_CHANGED_MSG, wid, hei, holder));
    }
    public void sendSurfaceDestroyed(SurfaceHolder holder) {
        sendMessage(obtainMessage(SURFACE_DESTROYED_MSG, holder));
    }
    public void resetSurfaceTextureToListener() {
        sendMessage(obtainMessage(SET_SURFACE_TEXTURE_TO_LISTENER_MSG));
    }
    public void shutdown() {sendMessage(obtainMessage(SHUTDOWN_MSG));}

    @Override
    public void handleMessage(Message message) {
        int mess = message.what;
        RenderThread thread = mRenderThreadWeakReference.get();
        if (thread == null) {
            Log.w(TAG, "CameraThreadHandler: thread is null");
            return;
        }

        switch (mess) {
            case SHUTDOWN_MSG:
                thread.shutdown();
                break;
            case SURFACE_CREATED_MSG:
                thread.surfaceCreated((SurfaceHolder) message.obj);
                break;
            case SURFACE_CHANGED_MSG:
                thread.surfaceChanged((SurfaceHolder) message.obj, message.arg1, message.arg2);
                break;
            case SURFACE_DESTROYED_MSG:
                thread.surfaceDestroyed((SurfaceHolder) message.obj);
                break;
            case SET_SURFACE_TEXTURE_TO_LISTENER_MSG:
                thread.resetSurfaceTextureToListener();
                break;
            case CAMERA_SIZE_MSG:
                thread.setCameraImageSize(message.arg1, message.arg2);
                break;
            case CAMERA_ROTATION:
                thread.setCameraRotation(message.arg1);
//                Log.d(TAG, "Camera_Rotation: " + message.arg1);
                break;
            default:
                throw new RuntimeException("unknown message id: " + mess);
        }
    }
}
