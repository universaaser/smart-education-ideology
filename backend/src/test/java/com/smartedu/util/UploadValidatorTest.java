package com.smartedu.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadValidatorTest {

    @Test
    void shouldNotAllowBlankExtensionWhenAllowListHasTrailingComma() {
        assertFalse(UploadValidator.isAllowedType("", "pdf,doc,"));
        assertFalse(UploadValidator.isAllowedType("   ", "pdf,doc,"));
        assertTrue(UploadValidator.isAllowedType("pdf", "pdf,doc,"));
    }
}
