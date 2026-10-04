package com.quephoto.importing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record ImportManifest (
        @NotNull @Positive @Max(9007199254740991L) Long portfolioId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid ImageItem> images,
        @NotBlank @Size(max = 500) String coverObjectKey
) {
    public record ImageItem(
            @NotBlank @Size(max = 500) String objectKey,
            @NotBlank String imageType,
            @NotNull Integer sortOrder,
            Integer width,
            Integer height
    ) {}
}

