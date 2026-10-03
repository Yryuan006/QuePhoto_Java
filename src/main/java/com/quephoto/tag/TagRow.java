package com.quephoto.tag;

/**
 * 标签查询结果；仅包含当前查询列，不含 created_at。
 * Service 根据 groupId 分组后组装标签树 DTO。
 * Long、Integer 可保存 null，不会改变数据库的非空约束。
 */
public class TagRow {

    /** tag.id：标签主键。 */
    private Long id;

    /** tag.group_id：所属组编号，外键指向 tag_group.id。 */
    private Long groupId;

    /** tag.name：标签名，例如“街拍”；同组内不能重名。 */
    private String name;

    /** tag.sort_order：排序值，通过驼峰配置映射到 sortOrder。 */
    private Integer sortOrder;

    /** 供 MyBatis 创建对象并填充查询结果。 */
    public TagRow() {
    }

    // getter 读取属性，setter 给属性赋值。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
