package com.quephoto.portfolio;

import com.quephoto.common.dto.PageResponse;
import com.quephoto.portfolio.dto.PublicPortfolioDtos;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {
    private final PortfolioService service;
    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PublicPortfolioDtos.ListItem> list(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(name = "tagId", required = false)
            @Min(1) @Max(9007199254740991L) Long tagId) {
        return service.publicPage(page, pageSize, tagId);
    }

    @GetMapping("/{id}")
    public PublicPortfolioDtos.Detail detail(@PathVariable("id")
                                             @Min(1) @Max(9007199254740991L) long id) {
        return service.publicDetail(id);
    }
}
