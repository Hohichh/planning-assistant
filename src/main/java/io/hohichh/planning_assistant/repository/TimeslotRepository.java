package io.hohichh.planning_assistant.repository;

import io.hohichh.planning_assistant.model.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimeslotRepository extends JpaRepository<Timeslot, UUID> {

    List<Timeslot> findByUserIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
            UUID userId, Instant from, Instant to);

    List<Timeslot> findByUserIdAndIsCommitted(UUID userId, Boolean isCommitted);

    void deleteByUserIdAndIsCommitted(UUID userId, Boolean isCommitted);
}