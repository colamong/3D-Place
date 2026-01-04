package com.colombus.media.contract.dto;

import java.util.UUID;

import com.colombus.media.contract.enums.AssetPurposeCode;
import com.colombus.media.contract.enums.SubjectKindCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PresignPutRequest(
    @NotNull SubjectKindCode kind,
    @NotNull UUID subjectId,
    @NotNull AssetPurposeCode purpose,
    @NotNull @Pattern(regexp = "image/(jpeg|png|webp)")
    String contentType,
    String originalFileName,
    String checksumSHA256Base64
) {}
