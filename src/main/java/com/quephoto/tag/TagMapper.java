package com.quephoto.tag;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper {
    List<TagGroupRow> findGroups();
    List<TagRow> findAllTags();
}