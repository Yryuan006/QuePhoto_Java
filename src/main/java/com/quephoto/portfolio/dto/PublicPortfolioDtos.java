package com.quephoto.portfolio.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开作品 DTO。Service 将 Row 的内部数据转换为接口需要的字段和图片 URL。
 * record 自动提供构造器和 id()/title() 等访问方法，不需要 getter/setter。
 */
public final class PublicPortfolioDtos {

    // 外层类仅用于组织相关 DTO，无需创建实例。
    private PublicPortfolioDtos() {}

    /** 列表摘要；拍摄时间应结合 shotTimePrecision 展示。 */
    public record ListItem(Long id, String title, String location,
                           LocalDateTime shotAt, String shotTimePrecision, String coverImageUrl) {}

    /** 详情中的图片；Integer 宽高保留未知值 null。 */
    public record Image(Long id, String imageType, String imageUrl,
                        Integer sortOrder, Integer width, Integer height) {}

    /** 一个已选标签及其所属组。 */
    public record Tag(Long groupId, String groupName, Long tagId, String tagName) {}

    /** 作品详情；coverImageUrl 是封面地址，图片和标签由 Service 组装。 */
    public record Detail(Long id, String title, String description, String location,
                         LocalDateTime shotAt, String shotTimePrecision, String coverImageUrl,
                         String shareImageUrl, List<Image> images, List<Tag> tags) {}
}
