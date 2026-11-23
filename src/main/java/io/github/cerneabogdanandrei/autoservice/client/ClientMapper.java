package io.github.cerneabogdanandrei.autoservice.client;

import io.github.cerneabogdanandrei.autoservice.client.dto.ClientRequest;
import io.github.cerneabogdanandrei.autoservice.client.dto.ClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
interface ClientMapper {

    ClientResponse toResponse(Client client);

    Client toEntity(ClientRequest request);

    void update(ClientRequest request, @MappingTarget Client client);
}
