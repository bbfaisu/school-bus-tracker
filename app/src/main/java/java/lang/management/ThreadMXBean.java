package java.lang.management;

public interface ThreadMXBean {
    int getThreadCount();
    long[] getAllThreadIds();
}
