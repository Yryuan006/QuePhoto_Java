package com.quephoto.tag;

import com.quephoto.tag.dto.TagTreeDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TagService {
    private final TagMapper mapper;
    public TagService(TagMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Group> tree() {
        var groups = mapper.findGroups();
        var tags = mapper.findAllTags();
        Map<Long, List<TagItem>> byGroup = new HashMap<>();
        for (TagRow tag : tags) {
            byGroup.computeIfAbsent(tag.getGroupId(), ignored -> new ArrayList<>())
                    .add(new TagItem(tag.getId(), tag.getName(), tag.getSortOrder()));
        }
        var result = new ArrayList<Group>();
        for (TagGroupRow group : groups) {
            result.add(new Group(group.getId(), group.getName(), group.getSortOrder(),
                    byGroup.getOrDefault(group.getId(), List.of())));
        }
        return result;
    }
}
