package com.stream_forge.streamforge.exception.globalExceptionHandler;

import com.stream_forge.streamforge.exception.EncodingException;
import com.stream_forge.streamforge.exception.VideoNotFoundException;
import com.stream_forge.streamforge.exception.VideoNotReadyToStreamException;
import com.stream_forge.streamforge.exception.VideoProbeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(VideoNotFoundException.class)
    public ResponseEntity<String> handleVideoNotFoundException(VideoNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(VideoNotReadyToStreamException.class)
    public ResponseEntity<String> handleVideoNotReadyException(VideoNotReadyToStreamException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ex.getMessage());
    }

    @ExceptionHandler(VideoProbeException.class)
    public ResponseEntity<String> handleVideoNotFoundException(VideoProbeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }


    @ExceptionHandler(EncodingException.class)
    public ResponseEntity<String> handleEncodingException(EncodingException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        return ResponseEntity.badRequest().body(errors); // 400
    }

}
