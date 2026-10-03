package com.quephoto.portfolio;

import java.time.LocalDateTime;

/**
 * 图片查询结果。Long/Integer 可以为 null，未知宽高不能用 0 代替。
 * Row 保存原始 objectKey，Service 生成 URL 后再组装图片 DTO。
 */
public class PortfolioImageRow {

    /** 图片记录编号。 */
    private Long id;

    /** 所属作品编号，对应 portfolio_id。 */
    private Long portfolioId;

    /** 图片类型：work（作品图）或 scene（场景图）。 */
    private String imageType;

    /** OSS 对象 Key，不是完整 URL 或图片文件内容。 */
    private String objectKey;

    /** 排序值，对应 sort_order；排序仍由 SQL 的 ORDER BY 指定。 */
    private Integer sortOrder;

    /** 宽度，单位像素；未知时为 null。 */
    private Integer width;

    /** 高度，单位像素；与 width 同时有值或同时为空。 */
    private Integer height;

    /** 登记时间，约定 UTC；当前 findImages 未查询此列，结果中保持 null。 */
    private LocalDateTime createdAt;

    /** 供 MyBatis 创建空对象，再按查询列填充属性。 */
    public PortfolioImageRow() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
