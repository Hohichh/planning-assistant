package io.hohichh.planning_assistant.repository;

import io.hohichh.planning_assistant.model.AuthMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthMethodRepository extends JpaRepository<AuthMethod, UUID> {

    List<AuthMethod> findByUserId(UUID userId);
}
