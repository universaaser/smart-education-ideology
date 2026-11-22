package com.smartedu.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Keeps the container-level upload rejection in the same response envelope as controller validation.
 */
@RestControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return Result.error(413, "FILE_TOO_LARGE: Upload exceeds the configured maximum size");
    }
}
