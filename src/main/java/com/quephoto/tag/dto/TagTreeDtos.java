package com.quephoto.tag.dto;

import java.util.List;

public final class TagTreeDtos {
    private TagTreeDtos() {}
    public record TagItem(Long id, String name, Integer sortOrder) {}
    public record Group(Long id, String name, Integer sortOrder, List<TagItem> tags) {}
}