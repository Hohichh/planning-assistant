package io.hohichh.planning_assistant.mapping;

import io.hohichh.planning_assistant.dto.TimeslotResponse;
import io.hohichh.planning_assistant.model.Timeslot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TimeslotMapper {

    @Mapping(source = "subtask.id", target = "subtaskId")
    @Mapping(source = "user.id", target = "userId")
    TimeslotResponse toResponse(Timeslot timeslot);
}
