package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.LoadOutcome;
import com.dkozel.velocitylimiter.service.LoadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
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
        return switch (loadService.process(request.toCommand())) {
            case LoadOutcome.Decided(LoadDecision decision) -> ResponseEntity.ok(LoadResponse.from(decision));
            // a retry, e.g. after a timeout: the original answer, marked as replayed
            case LoadOutcome.Replayed(LoadDecision original) -> ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(LoadResponse.from(original));
            case LoadOutcome.Conflicting _ -> throw new ErrorResponseException(HttpStatus.CONFLICT,
                    ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                            "This load id was already used for this customer with a different amount or time."), null);
        };
    }
}
