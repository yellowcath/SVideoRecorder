package us.pinguo.svideo.encoder;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaEncoder.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.util.Log;
import us.pinguo.svideo.recorder.OnRecordFailListener;
import us.pinguo.svideo.utils.RL;
import us.pinguo.svideo.utils.TimeOutThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CountDownLatch;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public abstract class MediaEncoderApi16 implements Runnable {
    private static final boolean DEBUG = false;    // TODO set false on release
    private static final String TAG = "MediaEncoder";

    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]
    protected static final int MSG_FRAME_AVAILABLE = 1;
    protected static final int MSG_STOP_RECORDING = 9;

    public interface MediaEncoderListener {
        public void onPrepared(MediaEncoderApi16 encoder);

        public void onStopped(MediaEncoderApi16 encoder);
    }

    protected final Object mSync = new Object();
    /**
     * Flag that indicate this encoder is capturing now.
     */
    protected volatile boolean mIsCapturing;
    /**
     * Flag that indicate the frame data will be available soon.
     */
    private int mRequestDrain;
    /**
     * Flag to request stop capturing
     */
    protected volatile boolean mRequestStop;
    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected boolean mIsEOS;
    /**
     * MediaCodec instance for encoding
     */
    protected MediaCodec mMediaCodec;                // API >= 16(Android4.1.2)
    /**
     * BufferInfo instance for dequeuing
     */
    private MediaCodec.BufferInfo mBufferInfo;        // API >= 16(Android4.1.2)

    protected final MediaEncoderListener mListener;

    private Thread mMediaEncoderThread;

    private OnRecordFailListener onRecordFailListener;

    protected CountDownLatch mCountDownLatch;

    protected FileChannel mFileChannel;

    public MediaEncoderApi16(final String outputFilePath, final MediaEncoderListener listener, CountDownLatch countDownLatch) {
        if (listener == null) {
            throw new NullPointerException("MediaEncoderListener is null");
        }
        if (outputFilePath == null) {
            throw new NullPointerException("outputFilePath is null");
        }
        mCountDownLatch = countDownLatch;
        mListener = listener;
        File outputFile = new File(outputFilePath);
        try {
            outputFile.createNewFile();
            mFileChannel = new FileOutputStream(outputFile).getChannel();
        } catch (IOException e) {
            throwRecordError(e);
            return;
        }
        synchronized (mSync) {
            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = new MediaCodec.BufferInfo();
            // wait for starting thread
            mMediaEncoderThread = new TimeOutThread(this, getClass().getSimpleName(), mCountDownLatch);
            mMediaEncoderThread.start();
            try {
                mSync.wait();
            } catch (final InterruptedException e) {
            }
        }
    }

    /**
     * the method to indicate frame data is soon available or already available
     *
     * @return return true if encoder is ready to encod.
     */
    public boolean frameAvailableSoon() {
//    	if (DEBUG) Log.v(TAG, "frameAvailableSoon");
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false;
            }
            mRequestDrain++;
            mSync.notifyAll();
        }
        return true;
    }

    /**
     * encoding loop on private thread
     */
    @Override
    public void run() {
//		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        synchronized (mSync) {
            mRequestStop = false;
            mRequestDrain = 0;
            mSync.notify();
        }
        final boolean isRunning = true;
        boolean localRequestStop;
        boolean localRequestDrain;
        try {
            while (isRunning) {
                synchronized (mSync) {
                    localRequestStop = mRequestStop;
                    localRequestDrain = (mRequestDrain > 0);
                    if (localRequestDrain) {
                        mRequestDrain--;
                    }
                }
                if (localRequestStop) {
                    drain();
                    // request stop recording
                    signalEndOfInputStream();
                    // process output data again for EOS signale
                    drain();
                    // release all related objects
                    release();
                    break;
                }
                if (localRequestDrain) {
                    drain();
                } else {
                    synchronized (mSync) {
                        try {
                            mSync.wait();
                        } catch (final InterruptedException e) {
                            break;
                        }
                    }
                }
            } // end of while
        } catch (Exception e) {
            throwRecordError(e);
        }
        if (DEBUG) {
            Log.d(TAG, "Encoder thread exiting");
        }
        synchronized (mSync) {
            mRequestStop = true;
            mIsCapturing = false;
        }
    }

    /*
    * prepareing method for each sub class
    * this method should be implemented in sub class, so set this as abstract method
    * @throws IOException
    */
   /*package*/
    abstract void prepare() throws IOException;

    /*package*/ void startRecording() {
        if (DEBUG) {
            Log.v(TAG, "startRecording");
        }
        synchronized (mSync) {
            mIsCapturing = true;
            mRequestStop = false;
            mSync.notifyAll();
        }
    }

    /**
     * the method to request stop encoding
     */
    /*package*/
    public void stopRecording() {
        if (DEBUG) {
            Log.v(TAG, "stopRecording");
        }
        synchronized (mSync) {
            if (!mIsCapturing || mRequestStop) {
                return;
            }
            mRequestStop = true;    // for rejecting newer frame
            mSync.notifyAll();
            // We can not know when the encoding and writing finish.
            // so we return immediately after request to avoid delay of caller thread
        }
    }

//********************************************************************************
//********************************************************************************

    /**
     * Release all releated objects
     */
    protected void release() {
        if (DEBUG) {
            Log.d(TAG, "release:");
        }
        try {
            mListener.onStopped(this);
        } catch (final Exception e) {
            Log.e(TAG, "failed onStopped", e);
        }
        mIsCapturing = false;
        if (mMediaCodec != null) {
            try {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            } catch (final Exception e) {
                Log.e(TAG, "failed releasing MediaCodec", e);
            }
        }
        mBufferInfo = null;
        if (mFileChannel != null) {
            try {
                mFileChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void signalEndOfInputStream() {
        if (DEBUG) {
            Log.d(TAG, "sending EOS to encoder");
        }
        // signalEndOfInputStream is only avairable for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
//		mMediaCodec.signalEndOfInputStream();	// API >= 18
        encode(null, 0, getPTSUs());
    }

    /**
     * Method to set byte array to the MediaCodec encoder
     *
     * @param buffer
     * @param length             　length of byte array, zero means EOS.
     * @param presentationTimeUs
     */
    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        if (!mIsCapturing) {
            return;
        }
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        while (mIsCapturing) {
            final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                if (buffer != null) {
                    inputBuffer.put(buffer);
                }
//	            if (DEBUG) Log.v(TAG, "encode:queueInputBuffer");
                if (length <= 0) {
                    // send EOS
                    mIsEOS = true;
                    if (DEBUG) {
                        Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                    }
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0);
                }
                break;
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }

    /**
     * drain encoded data and write them to muxer
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void drain() {
        if (mMediaCodec == null) {
            return;
        }
        ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
        int encoderStatus, count = 0;
        if (mFileChannel == null) {
//        	throw new NullPointerException("muxer is unexpectedly null");
            Log.w(TAG, "mFileChannel is unexpectedly null");
            return;
        }
        LOOP:
        while (mIsCapturing) {
            // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
            encoderStatus = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
                if (!mIsEOS) {
                    if (++count > 5) {
                        break LOOP;        // out of while
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (DEBUG) {
                    Log.v(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                }
                // this shoud not come when encoding
                encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (DEBUG) {
                    Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                }
            } else if (encoderStatus < 0) {
                // unexpected status
                if (DEBUG) {
                    Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
                }
            } else {
                final ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    // this never should come...may be a MediaCodec internal error
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
//                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                    // You shoud set output format to muxer here when you target Android4.3 or less
//                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
//                    // therefor we should expand and prepare output format from buffer data.
//                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
//                    if (DEBUG) {
//                        Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG");
//                    }
//                    mBufferInfo.size = 0;
//                }

                if (mBufferInfo.size != 0) {
                    // encoded data is ready, clear waiting counter
                    count = 0;
                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    mBufferInfo.presentationTimeUs = getPTSUs();

                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    try {
                        //ADTS header
                        byte[] header = new byte[7];
                        fillInADTSHeader(header, mBufferInfo.size + 7);
                        mFileChannel.write(ByteBuffer.wrap(header));
                        mFileChannel.write(encodedData);
                    } catch (IOException e) {
                        RL.e(e);
                    }
                    prevOutputPTSUs = mBufferInfo.presentationTimeUs;
                }
                // return buffer to encoder
                mMediaCodec.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // when EOS come.
                    mIsCapturing = false;
                    break;      // out of while
                }
            }
        }
    }

    /**
     * 直接录出来的音频是原始aac流，需要加上ADTS头才能播放
     *
     * @param packet
     * @param encoded_length
     */
    private void fillInADTSHeader(byte[] packet, int encoded_length) {
        int profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
//                (configParams[0]>>3)&0x1f;
        int frequency_index = 4;  //44.1KHz
//        int frequency_index = (this.configParams[0]&0x7) <<1 | (this.configParams[1]>>7) &0x1;
        int channel_config = 1;  //KEY_CHANNEL_COUNT
//        channel_config = (this.configParams[1]>>3) &0xf;

        int finallength = encoded_length + 7;
        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9; //0xF1?
        packet[2] = (byte) (((profile - 1) << 6) + (frequency_index << 2) + (channel_config >> 2));
        packet[3] = (byte) (((channel_config & 3) << 6) + (finallength >> 11));
        packet[4] = (byte) ((encoded_length & 0x7FF) >> 3);
        packet[5] = (byte) (((encoded_length & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    /**
     * previous presentationTimeUs for writing
     */
    private long prevOutputPTSUs = 0;

    /**
     * get next encoding presentationTimeUs
     *
     * @return
     */
    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs) {
            result = (prevOutputPTSUs - result) + result;
        }
        return result;
    }

    protected void throwRecordError(Exception e) {
        if (onRecordFailListener != null) {
            onRecordFailListener.onAudioRecordFail(e);
        }
        RL.e(e);
        release();
    }

    public void setOnRecordFailListener(OnRecordFailListener onRecordFailListener) {
        this.onRecordFailListener = onRecordFailListener;
    }
}
