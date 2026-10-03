package com.quephoto.portfolio;

/**
 * portfolio_tag、tag、tag_group 的联查投影，不是关联表的原样映射。
 * SQL 别名 group_id、group_name、tag_id、tag_name 通过驼峰配置映射到属性。
 * Row 供内部查询使用，Service 再将它转换成作品标签 DTO。
 * Long 可保存 null；正常联查会取得编号，刚创建的对象属性则为 null。
 */
public class PortfolioTagRow {

    /** tag_group.id：组编号，SQL 别名 group_id。 */
    private Long groupId;

    /** tag.id：标签编号，SQL 别名 tag_id。 */
    private Long tagId;

    /** tag_group.name：组名，SQL 别名 group_name。 */
    private String groupName;

    /** tag.name：标签名，SQL 别名 tag_name。 */
    private String tagName;

    /** 供 MyBatis 创建对象并填充查询结果。 */
    public PortfolioTagRow() {
    }

    // getter 读取属性，setter 给属性赋值。
    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
