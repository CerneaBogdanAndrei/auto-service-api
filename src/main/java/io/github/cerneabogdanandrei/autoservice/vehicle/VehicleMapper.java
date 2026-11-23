package io.github.cerneabogdanandrei.autoservice.vehicle;

import io.github.cerneabogdanandrei.autoservice.client.Client;
import io.github.cerneabogdanandrei.autoservice.vehicle.dto.VehicleRequest;
import io.github.cerneabogdanandrei.autoservice.vehicle.dto.VehicleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface VehicleMapper {

    VehicleResponse toResponse(Vehicle vehicle);

    VehicleResponse.ClientSummary toClientSummary(Client client);

    @Mapping(target = "client", ignore = true)
    Vehicle toEntity(VehicleRequest request);

    @Mapping(target = "client", ignore = true)
    void update(VehicleRequest request, @MappingTarget Vehicle vehicle);
}
