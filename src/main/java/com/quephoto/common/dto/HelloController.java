package com.quephoto.common.dto;

import com.quephoto.common.dto.HelloResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/api/hello")
    public HelloResponse hello(
            @RequestParam(defaultValue = "QuePhoto") String name) {
        return new HelloResponse("QuePhoto", "Hello" + name);
    }

}
