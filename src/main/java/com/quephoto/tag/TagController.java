package com.quephoto.tag;

import com.quephoto.tag.dto.TagTreeDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tag-groups")
public class TagController {
    private final TagService service;
    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public List<TagTreeDtos.Group> tree() {
        return service.tree();
    }
}
