package io.hohichh.planning_assistant.mapping;

import io.hohichh.planning_assistant.dto.UserRequest;
import io.hohichh.planning_assistant.dto.UserResponse;
import io.hohichh.planning_assistant.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(UserRequest request);

    UserResponse toResponse(User user);

    void updateEntity(UserRequest request, @MappingTarget User user);
}
