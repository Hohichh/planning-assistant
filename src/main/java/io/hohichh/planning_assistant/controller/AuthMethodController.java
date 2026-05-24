package io.hohichh.planning_assistant.controller;

import io.hohichh.planning_assistant.dto.AuthMethodRequest;
import io.hohichh.planning_assistant.dto.AuthMethodResponse;
import io.hohichh.planning_assistant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth-methods")
@RequiredArgsConstructor
public class AuthMethodController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<AuthMethodResponse> create(@Valid @RequestBody AuthMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuthMethodResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(authService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<AuthMethodResponse>> getByUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(authService.getByUserId(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuthMethodResponse> update(@PathVariable UUID id, @Valid @RequestBody AuthMethodRequest request) {
        return ResponseEntity.ok(authService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        authService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
