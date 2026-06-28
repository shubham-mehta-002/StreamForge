package com.stream_forge.streamforge.exception;

public class VideoNotReadyToStreamException extends RuntimeException{
    public VideoNotReadyToStreamException(String message){
        super(message);
    }
}