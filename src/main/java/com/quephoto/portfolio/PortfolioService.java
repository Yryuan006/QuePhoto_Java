package com.quephoto.portfolio;

import com.quephoto.common.dto.PageResponse;
import com.quephoto.oss.ImageUrlService;
import com.quephoto.portfolio.dto.PublicPortfolioDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.quephoto.portfolio.dto.AdminPortfolioDtos;
import com.quephoto.portfolio.dto.PortfolioWriteRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;


@Service
public class PortfolioService {
    private final PortfolioMapper mapper;
    private final ImageUrlService imageUrls;
    public PortfolioService(PortfolioMapper mapper, ImageUrlService imageUrls) {
        this.mapper = mapper;
        this.imageUrls = imageUrls;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ListItem> publicPage(int page, int pageSize, Long tagId) {
        long offset = ((long) page - 1) * pageSize;
        long total = mapper.countPublished(tagId);
        var items = new ArrayList<ListItem>();
        for (PortfolioRow row : mapper.findPublishedPage(tagId, pageSize, offset)) {
            items.add(new ListItem(row.getId(), row.getTitle(), row.getLocation(),
                    row.getShotAt(), row.getShotTimePrecision(), imageUrls.url(row.getCoverObjectKey())));
        }
        return new PageResponse<>(items, page, pageSize,total);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail publicDetail(long id) {
        PortfolioRow row = mapper.findPublishedById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在");
        }
        var images = new ArrayList<Image>();
        for (PortfolioImageRow image : mapper.findImages(id)) {
            images.add(new Image(image.getId(), image.getImageType(),
                    imageUrls.url(image.getObjectKey()), image.getSortOrder(),
                    image.getWidth(), image.getHeight()));
        }
        var tags = new ArrayList<Tag>();
        for (PortfolioTagRow tag : mapper.findTags(id)) {
            tags.add(new Tag(tag.getGroupId(), tag.getGroupName(),
                    tag.getTagId(), tag.getTagName()));
        }
        String cover = imageUrls.url(row.getCoverObjectKey());
        return new Detail(row.getId(), row.getTitle(), row.getDescription(),
                row.getLocation(), row.getShotAt(), row.getShotTimePrecision(),
                cover, cover, images, tags);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AdminPortfolioDtos.ListItem> adminPage(
            int page, int pageSize, String status
    ) {
        // 1. 检查分页参数。
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "分页参数不合法");
        }

        // 2. 状态可以不传；传了就必须严格符合枚举值。
        if (status != null) {
            try {
                status = PortfolioStatus.parse(status).value();
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "status只接受draft或published");
            }
        }

        // 3. 查询总数量和当前页。
        long offset = ((long) page - 1) * pageSize;
        long total = mapper.countAdmin(status);

        var items = new ArrayList<AdminPortfolioDtos.ListItem>();

        for (PortfolioRow row :
                mapper.findAdminPage(status, pageSize, offset)) {

            items.add(new AdminPortfolioDtos.ListItem(
                    row.getId(),
                    row.getTitle(),
                    row.getStatus(),
                    row.getLocation(),
                    row.getShotAt(),
                    row.getShotTimePrecision(),
                    imageUrls.url(row.getCoverObjectKey()),
                    row.getCreatedAt().toInstant(ZoneOffset.UTC),
                    row.getUpdatedAt().toInstant(ZoneOffset.UTC)
            ));
        }

        // 4. 返回与公开列表相同的分页结构。
        return new PageResponse<>(items, page, pageSize, total);
    }

    @Transactional
    public long createDraft(PortfolioWriteRequest request) {
        String title = request.title() == null
                ? ""
                : request.title().strip();

        if (title.isBlank() || title.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "标题不能为空，且不能超过100个字符");
        }

        if (request.description() != null
                && request.description().length() > 10000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "描述不能超过10000个字符");
        }

        if (request.location() != null
                && request.location().length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "地点不能超过200个字符");
        }

        if (!"draft".equals(request.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "创建作品时status只能为draft");
        }

        LocalDateTime shotAt = normalizeShotAt(
                request.shotAt(),
                request.shotTimePrecision());

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);

        PortfolioRow row = new PortfolioRow();
        row.setTitle(title);
        row.setDescription(request.description());
        row.setLocation(request.location());
        row.setShotAt(shotAt);
        row.setShotTimePrecision(request.shotTimePrecision());
        row.setStatus("draft");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        int affectedRows = mapper.insertDraft(row);
        Long id = row.getId();

        if (affectedRows != 1 || id == null
                || id < 1 || id > 9_007_199_254_740_991L) {
            throw new IllegalStateException("创建草稿未取得有效ID");
        }

        return id;
    }

    private LocalDateTime normalizeShotAt(
            LocalDateTime shotAt,
            String precision
    ) {
        if (shotAt == null && precision == null) {
            return null;
        }

        if (shotAt == null || precision == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "拍摄时间和时间精度必须同时填写或同时为空");
        }

        if (shotAt.getYear() < 1000 || shotAt.getYear() > 9999) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "拍摄年份必须在1000到9999之间");
        }

        return switch (precision) {
            case "year" ->
                    shotAt.toLocalDate().withDayOfYear(1).atStartOfDay();
            case "month" ->
                    shotAt.toLocalDate().withDayOfMonth(1).atStartOfDay();
            case "day" ->
                    shotAt.toLocalDate().atStartOfDay();
            case "hour" ->
                    shotAt.withMinute(0).withSecond(0).withNano(0);
            case "minute" ->
                    shotAt.withSecond(0).withNano(0);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "时间精度只接受year、month、day、hour、minute");
        };
    }
}


























