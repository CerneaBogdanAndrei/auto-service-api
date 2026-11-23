package io.github.cerneabogdanandrei.autoservice.user;

import io.github.cerneabogdanandrei.autoservice.user.dto.UserResponse;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    void update(UserUpdateRequest request, @MappingTarget User user);
}
