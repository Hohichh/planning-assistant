package io.hohichh.planning_assistant.service.strategy;

import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Timeslot;
import io.hohichh.planning_assistant.valueObj.AllocationConstraints;
import io.hohichh.planning_assistant.valueObj.ProposedTimeslot;

import java.util.List;

public interface SlotAllocationStrategy {

    List<ProposedTimeslot> allocate(List<Subtask> subtasks,
                                    List<Timeslot> occupied,
                                    AllocationConstraints constraints);
}
