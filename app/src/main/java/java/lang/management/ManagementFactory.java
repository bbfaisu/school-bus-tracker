package java.lang.management;

import java.util.ArrayList;
import java.util.List;

public class ManagementFactory {
    private static final RuntimeMXBean runtimeMXBean = new RuntimeMXBeanStub();
    private static final MemoryMXBean memoryMXBean = new MemoryMXBeanStub();
    private static final ThreadMXBean threadMXBean = new ThreadMXBeanStub();
    private static final OperatingSystemMXBean operatingSystemMXBean = new OperatingSystemMXBeanStub();

    public static RuntimeMXBean getRuntimeMXBean() {
        return runtimeMXBean;
    }

    public static MemoryMXBean getMemoryMXBean() {
        return memoryMXBean;
    }

    public static ThreadMXBean getThreadMXBean() {
        return threadMXBean;
    }

    public static OperatingSystemMXBean getOperatingSystemMXBean() {
        return operatingSystemMXBean;
    }

    private static class RuntimeMXBeanStub implements RuntimeMXBean {
        @Override
        public String getName() {
            return "12345@android";
        }

        @Override
        public List<String> getInputArguments() {
            return new ArrayList<>();
        }
    }

    private static class MemoryMXBeanStub implements MemoryMXBean {
        @Override
        public MemoryUsage getHeapMemoryUsage() {
            return new MemoryUsage(
                0,
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().totalMemory(),
                Runtime.getRuntime().maxMemory()
            );
        }

        @Override
        public MemoryUsage getNonHeapMemoryUsage() {
            return new MemoryUsage(0, 0, 0, 0);
        }
    }

    private static class ThreadMXBeanStub implements ThreadMXBean {
        @Override
        public int getThreadCount() {
            return Thread.activeCount();
        }

        @Override
        public long[] getAllThreadIds() {
            return new long[0];
        }
    }

    private static class OperatingSystemMXBeanStub implements OperatingSystemMXBean {
        @Override
        public String getName() {
            return "Android";
        }

        @Override
        public String getArch() {
            return System.getProperty("os.arch");
        }

        @Override
        public String getVersion() {
            return System.getProperty("os.version");
        }

        @Override
        public int getAvailableProcessors() {
            return Runtime.getRuntime().availableProcessors();
        }

        @Override
        public double getSystemLoadAverage() {
            return -1.0;
        }
    }
}
