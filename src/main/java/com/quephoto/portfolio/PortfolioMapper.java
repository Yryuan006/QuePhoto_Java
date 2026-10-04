package com.quephoto.portfolio;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 方法名对应 XML 的 select id；@Param 名称对应 SQL 中的参数名。 */
@Mapper
public interface PortfolioMapper {

    /** 统计已发布作品；tagId 为 null 时不筛标签，COUNT 返回数值，因此用 long。 */
    long countPublished(@Param("tagId") Long tagId);

    /** 分页查询作品；limit 是条数，offset 是跳过的行数。 */
    List<PortfolioRow> findPublishedPage(@Param("tagId") Long tagId,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    /** 查询已发布作品详情；未查到时返回 null。 */
    PortfolioRow findPublishedById(@Param("id") long id);

    /** 查询图片；公开 Service 应先确认作品已发布。 */
    List<PortfolioImageRow> findImages(@Param("portfolioId") long portfolioId);

    /** 联查标签和组名，返回 PortfolioTagRow 投影。 */
    List<PortfolioTagRow> findTags(@Param("portfolioId") long portfolioId);

    /** 统计管理列表中的作品；status 为 null 时查询所有状态。 */
    long countAdmin(@Param("status") String status);

    /** 分页查询管理列表，包含草稿。 */
    List<PortfolioRow> findAdminPage(
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    int insertDraft(PortfolioRow row);

    PortfolioRow findByIdForUpdate(@Param("id") long id);

    PortfolioImageRow findImageByObjectKey(
            @Param("objectKey") String objectKey);

    PortfolioImageRow findImageById(@Param("id") long id);

    int insertImage(PortfolioImageRow row);

    int setCover(
            @Param("portfolioId") long portfolioId,
            @Param("imageId") long imageId,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
