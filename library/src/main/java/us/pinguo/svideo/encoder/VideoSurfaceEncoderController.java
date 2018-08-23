/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.pinguo.svideo.encoder;

import android.graphics.Bitmap;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;
import us.pinguo.svideo.interfaces.OnSurfaceCreatedCallback;
import us.pinguo.svideo.recorder.OnRecordFailListener;
import us.pinguo.svideo.recorder.SSurfaceRecorder;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.TimeOutThread;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

/**
 * Encode a movie from frames rendered from an external texture image.
 * <p/>
 * The object wraps an encoder running partly on two different threads.  An external thread
 * is sending data to the encoder's input surface, and we (the encoder thread) are pulling
 * the encoded data out and feeding it into a MediaMuxer.
 * <p/>
 * We could block forever waiting for the encoder, but because of the thread decomposition
 * that turns out to be a little awkward (we want to call signalEndOfInputStream() from the
 * encoder thread to avoid thread-safety issues, but we can't do that if we're blocked on
 * the encoder).  If we don't pull from the encoder often enough, the producer side can back up.
 * <p/>
 * The solution is to have the producer trigger drainEncoder() on every frame, before it
 * submits the new frame.  drainEncoder() might run before or after the frame is submitted,
 * but it doesn't matter -- either it runs early and prevents blockage, or it runs late
 * and un-blocks the encoder.
 * <p/>
 * TODO: reconcile this with TextureMovieEncoder.
 */
public class VideoSurfaceEncoderController implements Runnable, Thread.UncaughtExceptionHandler {
    private static final boolean VERBOSE = true;

    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;

    // ----- accessed exclusively by encoder thread -----
    private VideoSurfaceEncoder mVideoEncoder;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;
    protected OnRecordFailListener mOnRecordFailListener;
    private CountDownLatch mCountDownLatch;
    protected boolean mIsSuccess;

    private Thread mThread;
    private OnSurfaceCreatedCallback mOnSurfaceCreatedCallback;

    private int mWidth, mHeight;

    private volatile Bitmap mLastFrameBitmap;
    private volatile long mLastFrameTimeNs;
    private Surface mSurface;
    private Object mWaitBitmapLock = new Object();
    private volatile Looper mThreadLooper;

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p/>
     * Creates a new thread, which will own the provided VideoEncoderCore.  When the
     * thread exits, the VideoEncoderCore will be released.
     * <p/>
     * Returns after the recorder thread has started and is ready to accept Messages.
     */
    public VideoSurfaceEncoderController(int width, int height, int bitRate, int frameRate, int iFrameInterval, MediaMuxer mediaMuxer, CountDownLatch countDownLatch, OnRecordFailListener onRecordFailListener) {
        mWidth = width;
        mHeight = height;
        this.mOnRecordFailListener = onRecordFailListener;
        mCountDownLatch = countDownLatch;
        boolean afterApi21 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        if (afterApi21 && SSurfaceRecorder.MEDIACODEC_API21_ENABLE) {
            mVideoEncoder = new VideoSurfaceEncoderApi21(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
        } else if (afterApi21 && SSurfaceRecorder.MEDIACODEC_API21_ASYNC_ENABLE) {
            mVideoEncoder = new VideoSurfaceEncoderAsyncApi21(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
        } else {
            mVideoEncoder = new VideoSurfaceEncoder(width, height, bitRate, frameRate, iFrameInterval, mediaMuxer);
        }
    }

    public Thread.State getState() {
        return mThread == null ? null : mThread.getState();
    }

    public void start() {
        synchronized (mReadyFence) {
            if (mRunning) {
                RL.w("Encoder thread already running");
                return;
            }
            mRunning = true;
            mThread = new TimeOutThread(this, "VideoSurfaceEncoderController", mCountDownLatch);
            mThread.start();
//            while (!mReady) {
//                try {
//                    mReadyFence.wait();
//                } catch (InterruptedException ie) {
//                    // ignore
//                }
//            }
        }
    }

    public void join(long time) throws InterruptedException {
        if (mThread != null && mThread.isAlive()) {
            mThread.join(time);
        }
    }

    public void join() throws InterruptedException {
        if (mThread != null && mThread.isAlive()) {
            mThread.join();
        }
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p/>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p/>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public void stopRecording() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    public void finish() {
        stopRecording();
    }

    public void finishAndWait() {
        stopRecording();
        RL.i("finishAndWait 1");
        synchronized (mReadyFence) {
            while (mRunning) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        RL.i("finishAndWait 2");
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    /**
     * Tells the video recorder that a new frame is arriving soon.  (Call from non-encoder thread.)
     * <p/>
     * This function sends a message and returns immediately.  This is fine -- the purpose is
     * to wake the encoder thread up to do work so the producer side doesn't block.
     */
    public void frameAvailableSoon(long timeUs) {
        RL.i("frameAvailableSoon:" + timeUs);
        synchronized (mReadyFence) {
            RL.i("synchronized (mReadyFence):" + mReady);
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE, timeUs));
    }

    public void frameAvailableSoon() {
        frameAvailableSoon(-1);
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p/>
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Thread.currentThread().setUncaughtExceptionHandler(this);
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mVideoEncoder.initInThread();
            mOnSurfaceCreatedCallback.onSurfaceCreate(getInputSurface());
            mReady = true;
            mReadyFence.notify();
        }
        mThreadLooper = Looper.myLooper();
        Looper.loop();

        RL.d("Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
            mIsSuccess = true;
            mReadyFence.notify();
        }
    }

    public boolean isSuccess() {
        return mIsSuccess;
    }

    public boolean isAlive() {
        return mThread.isAlive();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        mCountDownLatch.countDown();
        throwRecordError(ex);
    }

    public int getRecordedFrames() {
        return mVideoEncoder.getRecordedFrames();
    }

    public Surface getInputSurface() {
        return mVideoEncoder.getInputSurface();
    }

    public void setOnSurfaceCreatedCallback(OnSurfaceCreatedCallback onSurfaceCreatedCallback) {
        this.mOnSurfaceCreatedCallback = onSurfaceCreatedCallback;
    }

    public void setLastFrameBitmap(Bitmap mLastFrameBitmap, long lastFrameTimeNs) {
        synchronized (mWaitBitmapLock) {
            this.mLastFrameTimeNs = lastFrameTimeNs;
            this.mLastFrameBitmap = mLastFrameBitmap;
            mWaitBitmapLock.notifyAll();
        }
    }

    public Bitmap getLastFrameBitmapAndSetNull() {
        Bitmap bitmap = mLastFrameBitmap;
        mLastFrameBitmap = null;
        return bitmap;
    }

    public long getLastFrameTimeNsAndSetZero() {
        long timeNs = mLastFrameTimeNs;
        mLastFrameTimeNs = 0;
        return timeNs;
    }

    public Surface getSurfaceAndSetNull() {
        Surface surface = mSurface;
        mSurface = null;
        return surface;
    }

    public void setSurface(Surface surface) {
        mSurface = surface;
    }

    public void awakeForQuit() {
        if (mThread != null && mReadyFence != null && mThreadLooper != null) {
            synchronized (mReadyFence) {
                mReadyFence.notifyAll();
            }
        }
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<VideoSurfaceEncoderController> mWeakEncoder;

        public EncoderHandler(VideoSurfaceEncoderController encoder) {
            mWeakEncoder = new WeakReference<VideoSurfaceEncoderController>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            VideoSurfaceEncoderController encoder = mWeakEncoder.get();
            if (encoder == null) {
                RL.w("EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    Looper.myLooper().quit();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timeUs = (long) obj;
                    encoder.handleFrameAvailable(timeUs);
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

    /**
     * Handles notification of an available frame.
     */
    private void handleFrameAvailable(long timeUs) {
        if (VERBOSE) {
            RL.d("handleFrameAvailable");
        }
        long s = System.currentTimeMillis();
        mVideoEncoder.drainEncoder(false, timeUs);
        long e = System.currentTimeMillis();
        if (!(mVideoEncoder instanceof VideoSurfaceEncoderAsyncApi21)) {
            RL.i("drainEncoder:" + (e - s) + "ms" + " thread:" + Thread.currentThread());
        }
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        RL.d("handleStopRecording");
        //理论上这里时间传0也可以
        mVideoEncoder.drainEncoder(true, -1);
        mVideoEncoder.close();
    }

    public void throwRecordError(Throwable e) {
        if (mOnRecordFailListener != null) {
            mOnRecordFailListener.onVideoRecordFail(e, true);
        }
        RL.e(e);
        if (mVideoEncoder != null) {
            mVideoEncoder.close();
        }
    }
}
