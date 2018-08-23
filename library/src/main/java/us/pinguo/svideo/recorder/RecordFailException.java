package us.pinguo.svideo.recorder;

/**
 * Created by huangwei on 2015/12/26.
 */
public class RecordFailException extends RuntimeException {
    public RecordFailException(String msg) {
        super(msg);
    }

    public RecordFailException(Exception e) {
        super(e);
    }
}
