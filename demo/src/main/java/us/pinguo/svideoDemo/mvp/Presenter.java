package us.pinguo.svideoDemo.mvp;

public interface Presenter {

    public void attachView(ViewController controller);

    public void detachView();

}
