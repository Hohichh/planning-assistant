package io.hohichh.planning_assistant.service.strategy;

import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.valueObj.AllocationConstraints;
import io.hohichh.planning_assistant.valueObj.ProposedTimeslot;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class GreedySlotAllocationStrategy implements SlotAllocationStrategy {

    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DAY_END = LocalTime.of(22, 0);

    private static final LocalTime MORNING_START = LocalTime.of(8, 0);
    private static final LocalTime MORNING_END = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(18, 0);
    private static final LocalTime EVENING_START = LocalTime.of(18, 0);
    private static final LocalTime EVENING_END = LocalTime.of(22, 0);

    @Override
    public List<ProposedTimeslot> allocate(List<Subtask> subtasks,
                                           List<Timeslot> occupied,
                                           AllocationConstraints constraints) {
        List<OccupiedInterval> intervals = occupied.stream()
                .map(t -> new OccupiedInterval(t.getStartTime(), t.getEndTime()))
                .sorted(Comparator.comparing(OccupiedInterval::start))
                .toList();

        List<ProposedTimeslot> result = new ArrayList<>();
        Instant cursor = constraints.rangeStart();
        Instant rangeEnd = constraints.rangeEnd();
        int minBreak = constraints.minBreakMinutes() != null ? constraints.minBreakMinutes() : 10;
        int dailyBudgetUsed = 0;
        LocalDate currentDay = null;

        for (Subtask subtask : subtasks) {
            int durationMinutes = subtask.getEstimatedMinutes();
            Instant slotStart = findNextAvailableSlot(
                    cursor, durationMinutes, minBreak, intervals, constraints, rangeEnd,
                    dailyBudgetUsed, currentDay
            );

            if (slotStart == null) {
                break;
            }

            Instant slotEnd = slotStart.plus(Duration.ofMinutes(durationMinutes));
            result.add(new ProposedTimeslot(subtask.getId(), slotStart, slotEnd));

            cursor = slotEnd.plus(Duration.ofMinutes(minBreak));

            LocalDate slotDay = slotStart.atOffset(ZoneOffset.UTC).toLocalDate();
            if (currentDay == null || !currentDay.equals(slotDay)) {
                currentDay = slotDay;
                dailyBudgetUsed = durationMinutes;
            } else {
                dailyBudgetUsed += durationMinutes;
            }
        }

        return result;
    }

    private Instant findNextAvailableSlot(Instant cursor, int durationMinutes, int minBreak,
                                          List<OccupiedInterval> intervals,
                                          AllocationConstraints constraints,
                                          Instant rangeEnd,
                                          int dailyBudgetUsed,
                                          LocalDate currentDay) {
        Instant candidate = cursor;
        int maxIterations = 1000;

        for (int i = 0; i < maxIterations; i++) {
            if (!candidate.isBefore(rangeEnd)) {
                return null;
            }

            candidate = snapToWorkingHours(candidate, constraints);
            if (candidate == null || !candidate.isBefore(rangeEnd)) {
                return null;
            }

            if (constraints.avoidWeekends()) {
                candidate = skipWeekends(candidate);
                if (!candidate.isBefore(rangeEnd)) {
                    return null;
                }
            }

            LocalDate candidateDay = candidate.atOffset(ZoneOffset.UTC).toLocalDate();
            int budgetForDay = (currentDay != null && currentDay.equals(candidateDay))
                    ? dailyBudgetUsed : 0;

            if (constraints.maxDailyMinutes() != null
                    && budgetForDay + durationMinutes > constraints.maxDailyMinutes()) {
                candidate = nextDayStart(candidate, constraints);
                continue;
            }

            Instant slotEnd = candidate.plus(Duration.ofMinutes(durationMinutes));
            Instant windowEnd = dayEndInstant(candidate, constraints);
            if (slotEnd.isAfter(windowEnd)) {
                candidate = nextDayStart(candidate, constraints);
                continue;
            }

            Instant conflict = findConflict(candidate, slotEnd, intervals);
            if (conflict != null) {
                candidate = conflict.plus(Duration.ofMinutes(minBreak));
                continue;
            }

            return candidate;
        }

        return null;
    }

    private Instant snapToWorkingHours(Instant instant, AllocationConstraints constraints) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        LocalTime time = zdt.toLocalTime();

        LocalTime windowStart = preferredStart(constraints.preferredTimeOfDay());
        LocalTime windowEnd = preferredEnd(constraints.preferredTimeOfDay());

        if (time.isBefore(windowStart)) {
            return zdt.with(windowStart).toInstant();
        }
        if (!time.isBefore(windowEnd)) {
            return nextDayStart(instant, constraints);
        }
        return instant;
    }

    private Instant skipWeekends(Instant instant) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        DayOfWeek dow = zdt.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            return zdt.plusDays(2).with(DAY_START).toInstant();
        } else if (dow == DayOfWeek.SUNDAY) {
            return zdt.plusDays(1).with(DAY_START).toInstant();
        }
        return instant;
    }

    private Instant nextDayStart(Instant instant, AllocationConstraints constraints) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC).plusDays(1);
        LocalTime windowStart = preferredStart(constraints.preferredTimeOfDay());
        return zdt.with(windowStart).toInstant();
    }

    private Instant dayEndInstant(Instant instant, AllocationConstraints constraints) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        LocalTime windowEnd = preferredEnd(constraints.preferredTimeOfDay());
        return zdt.with(windowEnd).toInstant();
    }

    private Instant findConflict(Instant start, Instant end, List<OccupiedInterval> intervals) {
        for (OccupiedInterval interval : intervals) {
            if (start.isBefore(interval.end()) && end.isAfter(interval.start())) {
                return interval.end();
            }
        }
        return null;
    }

    private LocalTime preferredStart(String pref) {
        if (pref == null) return DAY_START;
        return switch (pref.toUpperCase()) {
            case "MORNING" -> MORNING_START;
            case "AFTERNOON" -> AFTERNOON_START;
            case "EVENING" -> EVENING_START;
            default -> DAY_START;
        };
    }

    private LocalTime preferredEnd(String pref) {
        if (pref == null) return DAY_END;
        return switch (pref.toUpperCase()) {
            case "MORNING" -> MORNING_END;
            case "AFTERNOON" -> AFTERNOON_END;
            case "EVENING" -> EVENING_END;
            default -> DAY_END;
        };
    }

    private record OccupiedInterval(Instant start, Instant end) {
    }
}
