package com.quephoto.portfolio;

import java.time.LocalDateTime;

/**
 * 作品查询结果。MyBatis 将下划线列名映射为驼峰属性，如 shot_at -> shotAt。
 * 未查询或未知的字段保留 null；Service 将 Row 转成对外返回的 DTO。
 */
public class PortfolioRow {

    /** 作品编号；Long 可表示 null。 */
    private Long id;

    /** 作品标题，对应 title。 */
    private String title;

    /** 作品描述，允许为 null。 */
    private String description;

    /** 拍摄地点，未知时为 null。 */
    private String location;

    /** 拍摄当地时间，不携带时区；未知时与精度一起为 null。 */
    private LocalDateTime shotAt;

    /** 时间精度：year/month/day/hour/minute，保留数据库小写值。 */
    private String shotTimePrecision;

    /** 状态：draft 或 published；Row 使用 String，业务枚举由 Service 转换。 */
    private String status;

    /** 封面图片编号；草稿可以没有封面。 */
    private Long coverImageId;

    /** 分享图的原始 OSS Key，不是访问 URL。 */
    private String shareImageKey;

    /** 创建时间，对应 created_at；本项目约定为 UTC。 */
    private LocalDateTime createdAt;

    /** 修改时间，对应 updated_at；由写入逻辑显式维护，约定为 UTC。 */
    private LocalDateTime updatedAt;

    /** 联查别名 cover_object_key 的结果，不是 portfolio 表中的列。 */
    private String coverObjectKey;

    /** 无参构造器：先创建对象，再填入查询结果。 */
    public PortfolioRow() {
    }

    // getter 读取属性；setter 修改内存中的属性，不会自动执行数据库 UPDATE。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id; // this.id 是对象字段，id 是传入参数。
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getShotAt() {
        return shotAt;
    }

    public void setShotAt(LocalDateTime shotAt) {
        this.shotAt = shotAt;
    }

    public String getShotTimePrecision() {
        return shotTimePrecision;
    }

    public void setShotTimePrecision(String shotTimePrecision) {
        this.shotTimePrecision = shotTimePrecision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCoverImageId() {
        return coverImageId;
    }

    public void setCoverImageId(Long coverImageId) {
        this.coverImageId = coverImageId;
    }

    public String getShareImageKey() {
        return shareImageKey;
    }

    public void setShareImageKey(String shareImageKey) {
        this.shareImageKey = shareImageKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCoverObjectKey() {
        return coverObjectKey;
    }

    public void setCoverObjectKey(String coverObjectKey) {
        this.coverObjectKey = coverObjectKey;
    }
}
