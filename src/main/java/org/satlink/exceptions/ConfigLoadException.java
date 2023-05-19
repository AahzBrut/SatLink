package org.satlink.exceptions;

public class ConfigLoadException extends RuntimeException{
    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
