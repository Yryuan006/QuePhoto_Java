package com.quephoto.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@Profile("import")
public class OssObjectVerifier {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final long MAX_SIZE =
            30L * 1024 * 1024;

    private final OSS oss;
    private final OssImportProperties properties;

    public OssObjectVerifier(
            OSS oss,
            OssImportProperties properties
    ) {
        this.oss = oss;
        this.properties = properties;
    }

    public ObjectMetadata verify(String objectKey) {

        validateObjectKey(objectKey);

        ObjectMetadata metadata =
                oss.getObjectMetadata(
                        properties.bucket(),
                        objectKey
                );

        String type = metadata.getContentType();

        String mime = type == null
                ? ""
                : type.split(";", 2)[0]
                .strip()
                .toLowerCase(Locale.ROOT);

        long size = metadata.getContentLength();

        if (!ALLOWED_MIME_TYPES.contains(mime)) {
            throw new IllegalArgumentException(
                    "图片 Content-Type 不受支持: " + mime
            );
        }

        if (size <= 0 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "图片大小必须在 0 到 30MiB 之间，实际大小: "
                            + size + " bytes"
            );
        }

        return metadata;
    }

    private void validateObjectKey(String objectKey) {

        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "objectKey 不能为空"
            );
        }

        if (!objectKey.startsWith(properties.allowedPrefix())) {
            throw new IllegalArgumentException(
                    "objectKey 必须位于允许的前缀下: "
                            + properties.allowedPrefix()
            );
        }
    }
}
