package sun.security.action;

import java.security.PrivilegedAction;
import java.util.Properties;

public class GetPropertyAction implements PrivilegedAction<String> {
    private final String key;
    private final String defaultValue;

    public GetPropertyAction(String key) {
        this(key, null);
    }

    public GetPropertyAction(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    @Override
    public String run() {
        return defaultValue == null ? System.getProperty(key) : System.getProperty(key, defaultValue);
    }

    public static String privilegedGetProperty(String key) {
        return System.getProperty(key);
    }

    public static String privilegedGetProperty(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

    public static Properties privilegedGetProperties() {
        return System.getProperties();
    }
}
