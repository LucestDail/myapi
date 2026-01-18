package com.example.myapi.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * SSE/Async 관련 예외 - 클라이언트 연결 종료 시 발생, 무시
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        // SSE 연결 종료 시 발생 - 정상 동작이므로 무시
        log.debug("Async request not usable (client disconnected): {}", ex.getMessage());
    }

    /**
     * IOException - Broken pipe 등 연결 종료 시 발생
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex, HttpServletRequest request) {
        String message = ex.getMessage();
        if (message != null && (message.contains("Broken pipe") || message.contains("Connection reset"))) {
            // 클라이언트 연결 종료 - 정상 동작이므로 무시
            log.debug("Client connection closed: {}", message);
        } else {
            log.error("IOException: {}", message);
        }
    }

    /**
     * IllegalStateException - async 관련 에러 포함
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String message = ex.getMessage();
        // SSE/Async 관련 에러는 무시
        if (message != null && (message.contains("async") || message.contains("Cannot start async"))) {
            log.debug("Async state error (client disconnected): {}", message);
            return null;  // 응답하지 않음
        }
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "INTERNAL_ERROR",
                        "message", message != null ? message : "Internal error",
                        "timestamp", Instant.now().toString()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "VALIDATION_ERROR",
                        "message", message,
                        "timestamp", Instant.now().toString()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        // SSE 요청에서의 예외는 무시
        String contentType = request.getHeader("Accept");
        if (contentType != null && contentType.contains("text/event-stream")) {
            log.debug("SSE request error ignored: {}", ex.getMessage());
            return null;
        }
        
        log.error("Unhandled exception: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "INTERNAL_ERROR",
                        "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                        "timestamp", Instant.now().toString()
                ));
    }
}
