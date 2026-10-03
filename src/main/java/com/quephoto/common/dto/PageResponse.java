package com.quephoto.common.dto;

import java.util.List;

/**
 * 通用分页 DTO；T 表示每条数据的类型，record 自动提供构造器和 items() 等访问方法。
 * @param items 本页数据，无结果时为空列表
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 * @param totalCount 符合条件的总记录数，不是本页条数
 * @param <T> 列表项类型
 */
public record PageResponse<T>(List<T> items, int page, int pageSize, long totalCount) {
}
