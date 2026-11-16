package com.agrigenius.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints used to exercise security and error handling in web slice tests. */
@RestController
@RequestMapping("/api/test")
public class TestApiController {

    public record CreateFarmRequest(@NotBlank String name, @Min(0) @Max(100000) Double areaAcres) {
    }

    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "pong");
    }

    @PostMapping("/farms")
    Map<String, Object> create(@Valid @RequestBody CreateFarmRequest request) {
        return Map.of("name", request.name());
    }

    @GetMapping("/boom")
    Map<String, String> boom() {
        throw new IllegalStateException("secret internal detail: db password=hunter2");
    }
}
