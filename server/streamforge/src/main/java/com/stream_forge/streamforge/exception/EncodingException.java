package com.stream_forge.streamforge.exception;

public class EncodingException extends RuntimeException {

    public EncodingException(String message) {
        super(message);
    }

    public EncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}