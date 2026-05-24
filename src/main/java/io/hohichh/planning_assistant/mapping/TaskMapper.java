package io.hohichh.planning_assistant.mapping;

import io.hohichh.planning_assistant.dto.TaskRequest;
import io.hohichh.planning_assistant.dto.TaskResponse;
import io.hohichh.planning_assistant.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "subtasks", ignore = true)
    Task toEntity(TaskRequest request);

    @Mapping(source = "user.id", target = "userId")
    TaskResponse toResponse(Task task);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "subtasks", ignore = true)
    void updateEntity(TaskRequest request, @MappingTarget Task task);
}
