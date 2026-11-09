package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.service.LoadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/loads")
class LoadController {

    private final LoadService loadService;

    LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @PostMapping
    ResponseEntity<LoadResponse> load(@Valid @RequestBody LoadRequest request) {
        return loadService.process(request.toCommand())
                .map(LoadResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build()); // TODO: duplicate status there
    }
}
