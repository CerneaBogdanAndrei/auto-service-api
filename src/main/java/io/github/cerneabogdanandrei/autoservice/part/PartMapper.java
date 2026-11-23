package io.github.cerneabogdanandrei.autoservice.part;

import io.github.cerneabogdanandrei.autoservice.part.dto.PartRequest;
import io.github.cerneabogdanandrei.autoservice.part.dto.PartResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
interface PartMapper {

    PartResponse toResponse(Part part);

    Part toEntity(PartRequest request);

    void update(PartRequest request, @MappingTarget Part part);
}
