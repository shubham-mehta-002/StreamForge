package com.stream_forge.streamforge.exception;

public class VideoProbeException extends RuntimeException{
    public VideoProbeException(String message, Throwable cause){
        super(message,cause);
    }

    public VideoProbeException(String message){
        super(message);
    }
}