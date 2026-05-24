package io.hohichh.planning_assistant.repository;

import io.hohichh.planning_assistant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    
}
