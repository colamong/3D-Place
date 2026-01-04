package com.colombus.common.web.core.exception.dto;

public record FieldError(
    String field,
    Object rejectedValue,
    String message
) {}
