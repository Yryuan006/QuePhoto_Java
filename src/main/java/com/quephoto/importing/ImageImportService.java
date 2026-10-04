package com.quephoto.importing;

import com.quephoto.portfolio.PortfolioImageRow;
import com.quephoto.portfolio.PortfolioMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Profile("import")
public class ImageImportService {
    private final PortfolioMapper mapper;

    public ImageImportService(PortfolioMapper mapper) {
        this.mapper = mapper;
    }

    // 放在类里面，因此外部用 ImageImportService.ImportResult。
    public record ImportResult(
            long portfolioId,
            int insertedCount,
            Map<String, Long> imageIds
    ) {}

    @Transactional
    public ImportResult applyVerifiedManifest(ImportManifest manifest) {
        // 第一个数据库操作就是锁作品；不能用 findPublishedById 替代。
        var portfolio = mapper.findByIdForUpdate(manifest.portfolioId());
        if (portfolio == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "作品不存在");
        }
        if (!"draft".equals(portfolio.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "只允许向草稿导入图片");
        }

        Map<String, Long> imageIds = new LinkedHashMap<>();
        int inserted = 0;
        for (var item : manifest.images()) {
            var existing = mapper.findImageByObjectKey(item.objectKey());
            if (existing != null) {
                if (!sameRegistration(existing, manifest.portfolioId(), item)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "ObjectKey 已被其他作品占用，或登记属性不同");
                }
                imageIds.put(item.objectKey(), existing.getId());
            } else {
                var row = toImageRow(manifest.portfolioId(), item);
                int affected = mapper.insertImage(row);
                if (affected != 1 || row.getId() == null) {
                    throw new IllegalStateException("插入图片未取得有效 ID");
                }
                imageIds.put(item.objectKey(), row.getId());
                inserted++;
            }
        }

        Long coverId = imageIds.get(manifest.coverObjectKey());
        if (coverId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "清单中没有指定封面");
        }
        var cover = mapper.findImageById(coverId);
        if (cover == null
                || !Objects.equals(cover.getPortfolioId(), manifest.portfolioId())
                || !"work".equals(cover.getImageType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "封面必须属于本作品且为 work");
        }

        // 相同清单重试时，封面不变就不再 UPDATE。
        if (!Objects.equals(portfolio.getCoverImageId(), coverId)) {
            int affected = mapper.setCover(
                    manifest.portfolioId(), coverId, nowUtc());
            if (affected != 1) {
                throw new IllegalStateException("设置封面失败");
            }
        }
        return new ImportResult(manifest.portfolioId(), inserted, imageIds);
    }

    private boolean sameRegistration(
            PortfolioImageRow row,
            Long portfolioId,
            ImportManifest.ImageItem item
    ) {
        return Objects.equals(row.getPortfolioId(), portfolioId)
                && Objects.equals(row.getObjectKey(), item.objectKey())
                && Objects.equals(row.getImageType(), item.imageType())
                && Objects.equals(row.getSortOrder(), item.sortOrder())
                && Objects.equals(row.getWidth(), item.width())
                && Objects.equals(row.getHeight(), item.height());
    }

    private PortfolioImageRow toImageRow(
            long portfolioId, ImportManifest.ImageItem item
    ) {
        if (!"work".equals(item.imageType())
                && !"scene".equals(item.imageType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "imageType 只允许 work 或 scene");
        }
        var row = new PortfolioImageRow();
        row.setPortfolioId(portfolioId);
        row.setObjectKey(item.objectKey());
        row.setImageType(item.imageType());
        row.setSortOrder(item.sortOrder());
        row.setWidth(item.width());
        row.setHeight(item.height());
        row.setCreatedAt(nowUtc());
        return row;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}