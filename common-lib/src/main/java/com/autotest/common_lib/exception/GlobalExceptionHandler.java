package com.autotest.common_lib.exception;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Import explicitly in servlet services; the gateway has a reactive handler. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(CommonException.class)
    public ResponseEntity<ErrorResponse> handleCommon(CommonException error) {
        return respond(error.getErrorCode(),error);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception error) {
        BaseErrorCode code;
        if(error instanceof MaxUploadSizeExceededException)code=CommonErrorCode.FILE_TOO_LARGE;
        else if(error instanceof org.springframework.web.ErrorResponse http)code=CommonErrorCode.forStatus(http.getStatusCode().value());
        else if(error instanceof IllegalArgumentException || error instanceof org.springframework.beans.TypeMismatchException || error instanceof org.springframework.validation.BindException
            || error instanceof org.springframework.http.converter.HttpMessageNotReadableException)code=CommonErrorCode.INVALID_INPUT_VALUE;
        else code=CommonErrorCode.INTERNAL_SERVER_ERROR;
        return respond(code,error);
    }
    private ResponseEntity<ErrorResponse> respond(BaseErrorCode code,Exception error) {
        String trace=UUID.randomUUID().toString();
        if(code.getStatus().is5xxServerError())log.error("Request failed code={} traceId={}",code.getCode(),trace,error);
        else log.warn("Request rejected code={} traceId={} exception={}",code.getCode(),trace,error.getClass().getSimpleName());
        return ResponseEntity.status(code.getStatus()).header("X-Trace-Id",trace).header("Cache-Control","no-store").body(ErrorResponse.of(code,trace));
    }
}
