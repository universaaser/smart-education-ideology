package com.smartedu.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadExceptionHandlerTest {

    @Test
    void shouldReturnUnified413EnvelopeForContainerSizeExceeded() {
        Result<Void> result = new UploadExceptionHandler()
                .handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(104857600L));

        assertEquals(413, result.getCode());
        assertTrue(result.getMessage().contains("FILE_TOO_LARGE"));
    }
}
