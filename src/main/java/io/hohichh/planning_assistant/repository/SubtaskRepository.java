package io.hohichh.planning_assistant.repository;

import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.model.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

    List<Subtask> findByTaskIdOrderBySortOrder(UUID taskId);

    List<Subtask> findByUserIdAndStatus(UUID userId, SubtaskStatus status);

    List<Subtask> findByUserIdAndStatusIn(UUID userId, Collection<SubtaskStatus> statuses);

    void deleteAllByUserIdAndStatus(UUID userId, SubtaskStatus status);
}