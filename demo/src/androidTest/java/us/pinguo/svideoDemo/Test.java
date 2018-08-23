package us.pinguo.svideoDemo;

/**
 * Created by huangwei on 2016/5/25.
 */
public class Test extends ApplicationTest {
    public void test() {
        try {
            throw new RuntimeException("asdasd");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
