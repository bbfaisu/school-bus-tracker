package java.lang.management;

import java.util.List;

public interface RuntimeMXBean {
    String getName();
    List<String> getInputArguments();
}
