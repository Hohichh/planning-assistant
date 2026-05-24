package io.hohichh.planning_assistant.service.strategy;

import io.hohichh.planning_assistant.enums.CognitiveLoad;
import io.hohichh.planning_assistant.enums.SubtaskStatus;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.valueObj.AllocationConstraints;
import io.hohichh.planning_assistant.valueObj.ProposedTimeslot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedySlotAllocationStrategyTest {

    private GreedySlotAllocationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GreedySlotAllocationStrategy();
    }

    @Test
    void allocate_noConflicts_slotsAssignedSequentially() {
        Instant rangeStart = LocalDate.of(2026, 5, 19) // Monday
                .atTime(LocalTime.of(9, 0)).toInstant(ZoneOffset.UTC);
        Instant rangeEnd = rangeStart.plus(Duration.ofDays(5));

        AllocationConstraints constraints = new AllocationConstraints(
                rangeStart, rangeEnd, 90, 10,
                null, "ANY", false, null);

        List<Subtask> subtasks = List.of(
                buildSubtask(45),
                buildSubtask(60)
        );

        List<ProposedTimeslot> result = strategy.allocate(subtasks, List.of(), constraints);

        assertEquals(2, result.size());
        assertEquals(rangeStart, result.get(0).startTime());
        assertEquals(rangeStart.plus(Duration.ofMinutes(45)), result.get(0).endTime());

        Instant expectedSecondStart = rangeStart.plus(Duration.ofMinutes(45 + 10));
        assertEquals(expectedSecondStart, result.get(1).startTime());
    }

    @Test
    void allocate_withConflict_skipsOccupied() {
        Instant rangeStart = LocalDate.of(2026, 5, 19)
                .atTime(LocalTime.of(9, 0)).toInstant(ZoneOffset.UTC);
        Instant rangeEnd = rangeStart.plus(Duration.ofDays(5));

        Timeslot occupied = Timeslot.builder()
                .startTime(rangeStart)
                .endTime(rangeStart.plus(Duration.ofMinutes(60)))
                .build();

        AllocationConstraints constraints = new AllocationConstraints(
                rangeStart, rangeEnd, 90, 10,
                null, "ANY", false, null);

        List<Subtask> subtasks = List.of(buildSubtask(30));

        List<ProposedTimeslot> result = strategy.allocate(subtasks, List.of(occupied), constraints);

        assertEquals(1, result.size());
        Instant expectedStart = rangeStart.plus(Duration.ofMinutes(60 + 10));
        assertEquals(expectedStart, result.get(0).startTime());
    }

    @Test
    void allocate_avoidWeekends_skipsSaturdayAndSunday() {
        Instant saturdayMorning = LocalDate.of(2026, 5, 23) // Saturday
                .atTime(LocalTime.of(9, 0)).toInstant(ZoneOffset.UTC);
        Instant rangeEnd = saturdayMorning.plus(Duration.ofDays(5));

        AllocationConstraints constraints = new AllocationConstraints(
                saturdayMorning, rangeEnd, 90, 10,
                null, "ANY", true, null);

        List<Subtask> subtasks = List.of(buildSubtask(45));

        List<ProposedTimeslot> result = strategy.allocate(subtasks, List.of(), constraints);

        assertEquals(1, result.size());
        Instant mondayStart = LocalDate.of(2026, 5, 25) // Monday
                .atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
        assertEquals(mondayStart, result.get(0).startTime());
    }

    @Test
    void allocate_preferredMorning_slotsWithinMorningWindow() {
        Instant rangeStart = LocalDate.of(2026, 5, 19)
                .atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
        Instant rangeEnd = rangeStart.plus(Duration.ofDays(5));

        AllocationConstraints constraints = new AllocationConstraints(
                rangeStart, rangeEnd, 90, 10,
                null, "MORNING", false, null);

        List<Subtask> subtasks = List.of(buildSubtask(45));

        List<ProposedTimeslot> result = strategy.allocate(subtasks, List.of(), constraints);

        assertEquals(1, result.size());
        Instant slotEnd = result.get(0).endTime();
        LocalTime endTime = slotEnd.atOffset(ZoneOffset.UTC).toLocalTime();
        assertTrue(endTime.isBefore(LocalTime.of(12, 1)));
    }

    @Test
    void allocate_maxDailyMinutes_overflowsToNextDay() {
        Instant rangeStart = LocalDate.of(2026, 5, 19)
                .atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
        Instant rangeEnd = rangeStart.plus(Duration.ofDays(5));

        AllocationConstraints constraints = new AllocationConstraints(
                rangeStart, rangeEnd, 90, 10,
                null, "ANY", false, 60);

        List<Subtask> subtasks = List.of(
                buildSubtask(45),
                buildSubtask(45)
        );

        List<ProposedTimeslot> result = strategy.allocate(subtasks, List.of(), constraints);

        assertEquals(2, result.size());
        LocalDate day1 = result.get(0).startTime().atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate day2 = result.get(1).startTime().atOffset(ZoneOffset.UTC).toLocalDate();
        assertFalse(day1.equals(day2), "Second subtask should overflow to next day");
    }

    @Test
    void allocate_emptySubtasks_returnsEmptyList() {
        Instant rangeStart = Instant.now();
        AllocationConstraints constraints = AllocationConstraints.withDefaults(rangeStart, rangeStart.plus(Duration.ofDays(1)));

        List<ProposedTimeslot> result = strategy.allocate(List.of(), List.of(), constraints);

        assertTrue(result.isEmpty());
    }

    private Subtask buildSubtask(int estimatedMinutes) {
        return Subtask.builder()
                .id(UUID.randomUUID())
                .title("Test subtask " + estimatedMinutes + "min")
                .estimatedMinutes(estimatedMinutes)
                .cognitiveLoad(CognitiveLoad.MEDIUM)
                .status(SubtaskStatus.DRAFT)
                .isCompleted(false)
                .build();
    }
}
