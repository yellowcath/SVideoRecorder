package us.pinguo.svideo.utils;

import java.util.concurrent.Semaphore;

/**
 * Created by huangwei on 2016/1/26.
 * 该类主要是为了打日志
 */
public class RecordSemaphore extends Semaphore {

    public RecordSemaphore(int permits) {
        super(permits);
    }

    @Override
    public void release(int permits) {
        super.release(permits);
        RL.i("release " + permits + " availablePermits:" + availablePermits());
    }

    @Override
    public void release() {
        super.release();
        RL.i("release " + 1 + " availablePermits:" + availablePermits());
    }

    @Override
    public boolean tryAcquire(int permits) {
        boolean rtn = super.tryAcquire(permits);
        RL.i("tryAcquire " + permits + " availablePermits:" + availablePermits());
        return rtn;
    }

    @Override
    public void acquire(int permits) throws InterruptedException {
        super.acquire(permits);
        RL.i("acquire " + permits + " availablePermits:" + availablePermits());
    }

    @Override
    public void acquire() throws InterruptedException {
        super.acquire();
        RL.i("acquire availablePermits:" + availablePermits());
    }
}