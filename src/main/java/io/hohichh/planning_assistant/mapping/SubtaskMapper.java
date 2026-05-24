package io.hohichh.planning_assistant.mapping;

import io.hohichh.planning_assistant.dto.SubtaskResponse;
import io.hohichh.planning_assistant.dto.SubtaskUpdateRequest;
import io.hohichh.planning_assistant.model.Subtask;
import io.hohichh.planning_assistant.model.Timeslot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring", uses = TimeslotMapper.class)
public interface SubtaskMapper {

    @Mapping(source = "task.id", target = "taskId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "replaces.id", target = "replacesId")
    @Mapping(source = "timeslots", target = "timeslot", qualifiedByName = "firstTimeslot")
    SubtaskResponse toResponse(Subtask subtask);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "task", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "isCompleted", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "replaces", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "timeslots", ignore = true)
    void updateEntity(SubtaskUpdateRequest request, @MappingTarget Subtask subtask);

    @Named("firstTimeslot")
    default Timeslot firstTimeslot(List<Timeslot> timeslots) {
        if (timeslots == null || timeslots.isEmpty()) {
            return null;
        }
        return timeslots.getFirst();
    }
}
