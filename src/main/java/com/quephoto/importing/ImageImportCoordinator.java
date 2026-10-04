package com.quephoto.importing;

import com.quephoto.oss.OssImportProperties;
import com.quephoto.oss.OssObjectVerifier;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@Profile("import")
public class ImageImportCoordinator {
    private final Validator validator;
    private final String allowPrefix;
    private final OssObjectVerifier verifier;
    private final ImageImportService importService;

    public ImageImportCoordinator(
            Validator validator,
            OssImportProperties properties,
            OssObjectVerifier verifier,
            ImageImportService importService
    ) {
        this.validator = validator;
        this.allowPrefix = properties.allowedPrefix();
        this.verifier = verifier;
        this.importService = importService;
        if (allowPrefix == null || allowPrefix.isBlank()
                || !allowPrefix.endsWith("/")) {
            throw new IllegalStateException(
                    "oss.allowed-prefix 必须非空且以 / 结尾");
        }
    }

    // Runner 只调用这一个入口；这里不要加 @Transactional。
    public ImageImportService.ImportResult importImages(ImportManifest manifest) {
        validateManifest(manifest);

        // 到这里，整份清单都已通过静态校验。
        for (var item : manifest.images()) {
            verifier.verify(item.objectKey());
        }

        // 所有 HEAD 成功之后，才进入另一个 Spring Bean 的事务。
        return importService.applyVerifiedManifest(manifest);
    }

    public void validateManifest(ImportManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("清单不能为 null");
        }
        var violations = validator.validate(manifest);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("清单基础校验失败: " + message);
        }

        var seenKeys = new HashSet<String>();
        ImportManifest.ImageItem coverItem = null;
        for (int i = 0; i < manifest.images().size(); i++) {
            var item = manifest.images().get(i);
            String field = "images[" + i + "]";
            validateObjectKey(item.objectKey(), field + ".objectKey");

            if (!"work".equals(item.imageType())
                    && !"scene".equals(item.imageType())) {
                throw new IllegalArgumentException(
                        field + ".imageType 只允许小写 work 或 scene");
            }

            Integer width = item.width();
            Integer height = item.height();
            if ((width == null) != (height == null)) {
                throw new IllegalArgumentException(
                        field + " 的 width 和 height 必须同时为空或同时填写");
            }
            if (width != null && (width <= 0 || height <= 0)) {
                throw new IllegalArgumentException(
                        field + " 的 width 和 height 必须为正整数");
            }

            if (!seenKeys.add(item.objectKey())) {
                throw new IllegalArgumentException(
                        field + ".objectKey 与前面的图片重复");
            }
            if (item.objectKey().equals(manifest.coverObjectKey())) {
                coverItem = item;
            }
        }

        // 注意这个位置：已经离开 for 循环，看过全部图片。
        validateObjectKey(manifest.coverObjectKey(), "coverObjectKey");
        if (coverItem == null) {
            throw new IllegalArgumentException(
                    "coverObjectKey 必须对应本清单中的一张图片");
        }
        if (!"work".equals(coverItem.imageType())) {
            throw new IllegalArgumentException("封面图片的 imageType 必须为 work");
        }
    }

    private void validateObjectKey(String key, String field) {
        // 非空和长度已由 ImportManifest 的注解检查。
        if (key.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) {
            throw new IllegalArgumentException(field + " 不能填写 URL");
        }
        if (key.startsWith("/") || key.contains("\\")
                || key.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " 不能以 / 开头，不能包含反斜杠或控制字符");
        }
        for (String segment : key.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        field + " 不能包含独立的 . 或 .. 路径段");
            }
        }
        if (!key.startsWith(allowPrefix)) {
            throw new IllegalArgumentException(
                    field + " 必须以 " + allowPrefix + " 开头");
        }
    }
}