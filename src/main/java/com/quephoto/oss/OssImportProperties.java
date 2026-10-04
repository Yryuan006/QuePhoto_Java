package com.quephoto.oss;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oss")
public record OssImportProperties(

        @NotBlank
        String region,

        @NotBlank
        String endpoint,

        @NotBlank
        String bucket,

        @NotBlank
        String allowedPrefix,

        @NotBlank
        String accessKeyId,

        @NotBlank
        String accessKeySecret

) {
}
