package com.financialguru.controller;

import com.financialguru.dto.response.SearchResult;
import com.financialguru.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResult search(@RequestParam(required = false) String q) {
        return searchService.search(q);
    }
}
