package com.quephoto.tag;

/**
 * 标签组查询结果；仅包含当前查询列，不含 created_at。
 * Row 在程序内部使用，DTO 用于接口返回。
 * Long、Integer 可保存 null，不会改变数据库的非空约束。
 */
public class TagGroupRow {

    /** tag_group.id：标签组主键。 */
    private Long id;

    /** tag_group.name：组名，例如“题材”。 */
    private String name;

    /** tag_group.sort_order：排序值，通过驼峰配置映射到 sortOrder。 */
    private Integer sortOrder;

    /** 供 MyBatis 创建对象并填充查询结果。 */
    public TagGroupRow() {
    }

    // getter 读取属性，setter 给属性赋值；以下字段采用相同写法。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
