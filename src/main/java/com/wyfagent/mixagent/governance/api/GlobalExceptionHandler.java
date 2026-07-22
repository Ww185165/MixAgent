package com.wyfagent.mixagent.governance.api;

import com.wyfagent.mixagent.knowledge.application.exception.DuplicateKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.application.exception.InvalidKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeApplicationException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeDocumentNotFoundException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeOperationConflictException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeSearchUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将接口边界处的异常转换为稳定错误协议，同时保留请求标识供服务端定位问题。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String UNKNOWN_REQUEST_ID = "unknown";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "REQUEST_VALIDATION_FAILED",
                "请求参数不符合要求",
                details
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                details.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "请求参数超出允许范围",
                details
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        log.debug("请求体无法解析，requestId={}", currentRequestId(), exception);
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST_BODY",
                "请求体格式不正确",
                Map.of()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "KNOWLEDGE_FILE_TOO_LARGE",
                "上传文件超过允许大小",
                Map.of()
        );
    }

    @ExceptionHandler(KnowledgeApplicationException.class)
    public ResponseEntity<ApiError> handleKnowledgeApplicationException(KnowledgeApplicationException exception) {
        HttpStatus status;
        if (exception instanceof KnowledgeDocumentNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception instanceof DuplicateKnowledgeDocumentException
                || exception instanceof KnowledgeOperationConflictException) {
            status = HttpStatus.CONFLICT;
        } else if (exception instanceof KnowledgeSearchUnavailableException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        Map<String, String> details = exception instanceof DuplicateKnowledgeDocumentException duplicate
                ? Map.of("existingDocumentId", Long.toString(duplicate.existingDocumentId()))
                : Map.of();
        return buildResponse(status, exception.code(), exception.getMessage(), details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        // 完整异常只记录在服务端，响应中使用统一文案，避免泄露数据库和第三方服务信息。
        log.error("未处理异常，requestId={}", currentRequestId(), exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "服务暂时不可用，请稍后重试",
                Map.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> details
    ) {
        ApiError error = new ApiError(code, message, currentRequestId(), Instant.now(), details);
        return ResponseEntity.status(status).body(error);
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY);
        return requestId == null || requestId.isBlank() ? UNKNOWN_REQUEST_ID : requestId;
    }
}
