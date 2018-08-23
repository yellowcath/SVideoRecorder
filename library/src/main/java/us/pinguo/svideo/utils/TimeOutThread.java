package us.pinguo.svideo.utils;


import java.util.concurrent.CountDownLatch;

/**
 * Created by huangwei on 2015/12/26.
 */
public class TimeOutThread extends Thread {

    protected CountDownLatch mCountDonwLatch;

    public TimeOutThread(CountDownLatch countDownLatch) {
        mCountDonwLatch = countDownLatch;
    }

    public TimeOutThread(String threadName, CountDownLatch countDownLatch) {
        super(threadName);
        mCountDonwLatch = countDownLatch;
    }

    public TimeOutThread(Runnable runnable, String threadName, CountDownLatch countDownLatch) {
        super(runnable, threadName);
        mCountDonwLatch = countDownLatch;
    }

    @Override
    public void run() {
        super.run();
        mCountDonwLatch.countDown();
    }
}
