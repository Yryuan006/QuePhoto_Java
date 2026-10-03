package com.quephoto.portfolio;

import com.quephoto.common.dto.PageResponse;
import com.quephoto.portfolio.dto.AdminPortfolioDtos;
import com.quephoto.portfolio.dto.PortfolioWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/portfolios")
public class AdminPortfolioController {
    private final PortfolioService service;

    public AdminPortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminPortfolioDtos.ListItem> list(
            @RequestParam(name = "page", defaultValue = "1")
            @Min(1) int page,

            @RequestParam(name = "pageSize", defaultValue = "20")
            @Min(1) @Max(50) int pageSize,

            @RequestParam(name = "status", required = false)
            String status
    ) {
        return service.adminPage(page, pageSize, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(
            @Valid @RequestBody PortfolioWriteRequest request
    ) {
        long id = service.createDraft(request);
        return Map.of("id", id);
    }
}