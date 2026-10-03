package com.quephoto.portfolio;

import com.quephoto.common.dto.PageResponse;
import com.quephoto.oss.ImageUrlService;
import com.quephoto.portfolio.dto.PublicPortfolioDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
}


























