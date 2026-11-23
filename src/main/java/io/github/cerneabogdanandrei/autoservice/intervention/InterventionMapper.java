package io.github.cerneabogdanandrei.autoservice.intervention;

import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentCost;
import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentCostCalculator;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionResponse;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionSummaryResponse;
import io.github.cerneabogdanandrei.autoservice.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
interface InterventionMapper {

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "assignee", source = "assignedUser")
    InterventionSummaryResponse toSummary(Intervention intervention);

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "assignee", source = "assignedUser")
    @Mapping(target = "partsCost", expression = "java(cost(intervention).parts())")
    @Mapping(target = "total", expression = "java(cost(intervention).total())")
    InterventionResponse toResponse(Intervention intervention);

    @Mapping(target = "partId", source = "part.id")
    @Mapping(target = "code", source = "part.code")
    @Mapping(target = "name", source = "part.name")
    @Mapping(target = "lineTotal", expression = "java(line.lineTotal())")
    InterventionResponse.PartLine toLine(InterventionPart line);

    InterventionResponse.AssigneeSummary toAssignee(User user);

    /** Same formula as the appointment total, applied to a single intervention. */
    default AppointmentCost cost(Intervention intervention) {
        return AppointmentCostCalculator.calculate(List.of(intervention.getLaborCost()),
                intervention.getParts().stream()
                        .map(line -> new AppointmentCostCalculator.PartLine(line.getQuantity(), line.getUnitPrice()))
                        .toList());
    }
}
