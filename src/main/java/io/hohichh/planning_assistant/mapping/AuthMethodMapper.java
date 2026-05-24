package io.hohichh.planning_assistant.mapping;

import io.hohichh.planning_assistant.dto.AuthMethodRequest;
import io.hohichh.planning_assistant.dto.AuthMethodResponse;
import io.hohichh.planning_assistant.model.AuthMethod;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AuthMethodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    AuthMethod toEntity(AuthMethodRequest request);

    @Mapping(source = "user.id", target = "userId")
    AuthMethodResponse toResponse(AuthMethod authMethod);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(AuthMethodRequest request, @MappingTarget AuthMethod authMethod);
}
