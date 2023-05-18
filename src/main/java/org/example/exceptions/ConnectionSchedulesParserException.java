package org.example.exceptions;

public class ConnectionSchedulesParserException extends RuntimeException{
    public ConnectionSchedulesParserException(String message) {
        super(message);
    }

    public ConnectionSchedulesParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
