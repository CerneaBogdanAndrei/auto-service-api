package io.github.cerneabogdanandrei.autoservice.appointment;

import io.github.cerneabogdanandrei.autoservice.appointment.dto.AppointmentResponse;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
interface AppointmentMapper {

    @Mapping(target = "cost", ignore = true)
    AppointmentResponse toResponse(Appointment appointment);

    @Mapping(target = "id", source = "appointment.id")
    @Mapping(target = "createdAt", source = "appointment.createdAt")
    @Mapping(target = "updatedAt", source = "appointment.updatedAt")
    @Mapping(target = "cost", source = "cost")
    AppointmentResponse toResponse(Appointment appointment, AppointmentCost cost);

    AppointmentResponse.VehicleSummary toVehicleSummary(Vehicle vehicle);
}
