package io.github.cerneabogdanandrei.autoservice.appointment;

import io.github.cerneabogdanandrei.autoservice.appointment.dto.AppointmentRequest;
import io.github.cerneabogdanandrei.autoservice.appointment.dto.AppointmentResponse;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.common.time.BusinessCalendar;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vehicle;
import io.github.cerneabogdanandrei.autoservice.vehicle.VehicleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scheduling rules:
 * <ul>
 *   <li>appointments cannot start in the past;</li>
 *   <li>non-cancelled appointments of the same vehicle cannot overlap;</li>
 *   <li>status follows {@link AppointmentStatus}'s state machine;</li>
 *   <li>only scheduled appointments can be rescheduled, and only scheduled or cancelled ones
 *       deleted (in-progress and completed ones carry work and costs).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private static final String RESOURCE = "Appointment";
    private static final Set<AppointmentStatus> DELETABLE = Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CANCELLED);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;
    private final VehicleService vehicleService;
    private final BusinessCalendar calendar;
    private final Clock clock;

    AppointmentService(AppointmentRepository appointmentRepository, AppointmentMapper appointmentMapper,
                       VehicleService vehicleService, BusinessCalendar calendar, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentMapper = appointmentMapper;
        this.vehicleService = vehicleService;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * @param from first day (inclusive, business time zone) the appointment may start on
     * @param to   last day (inclusive, business time zone) the appointment may start on
     */
    public Page<AppointmentResponse> findAll(LocalDate from, LocalDate to, AppointmentStatus status, Long vehicleId,
                                             Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException(ErrorCode.INVALID_PARAMETER, "'from' must not be after 'to'.");
        }
        Specification<Appointment> filter = Specification.allOf(
                AppointmentSpecifications.hasStatus(status),
                AppointmentSpecifications.forVehicle(vehicleId),
                AppointmentSpecifications.startsAtOrAfter(from == null ? null : calendar.startOf(from)),
                AppointmentSpecifications.startsBefore(to == null ? null : calendar.endOf(to)));
        return appointmentRepository.findAll(filter, pageable).map(appointmentMapper::toResponse);
    }

    public AppointmentResponse findById(Long id) {
        Appointment appointment = appointmentRepository.findWithVehicleById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        return withCost(appointment);
    }

    /** Labour of all interventions plus all parts used, at the prices recorded when they were used. */
    public AppointmentCost costOf(Long appointmentId) {
        List<AppointmentCostCalculator.PartLine> partLines = appointmentRepository.findPartLines(appointmentId).stream()
                .map(line -> new AppointmentCostCalculator.PartLine(line.getQuantity(), line.getUnitPrice()))
                .toList();
        return AppointmentCostCalculator.calculate(appointmentRepository.findLaborCosts(appointmentId), partLines);
    }

    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        Vehicle vehicle = vehicleService.getVehicle(request.vehicleId());
        Instant startAt = request.startAt().toInstant();
        Instant endAt = request.endAt().toInstant();
        assertNotInPast(startAt);
        if (appointmentRepository.existsOverlapping(vehicle.getId(), startAt, endAt)) {
            throw overlap(vehicle.getId());
        }
        Appointment appointment = appointmentRepository.save(
                new Appointment(vehicle, startAt, endAt, trimToNull(request.notes())));
        return withCost(appointment);
    }

    @Transactional
    public AppointmentResponse update(Long id, AppointmentRequest request) {
        Appointment appointment = getAppointmentWithVehicle(id);
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw notModifiable(appointment, "rescheduled");
        }
        Vehicle vehicle = vehicleService.getVehicle(request.vehicleId());
        Instant startAt = request.startAt().toInstant();
        Instant endAt = request.endAt().toInstant();
        assertNotInPast(startAt);
        if (appointmentRepository.existsOverlappingExcluding(vehicle.getId(), startAt, endAt, id)) {
            throw overlap(vehicle.getId());
        }
        appointment.setVehicle(vehicle);
        appointment.setStartAt(startAt);
        appointment.setEndAt(endAt);
        appointment.setNotes(trimToNull(request.notes()));
        appointmentRepository.flush();
        return withCost(appointment);
    }

    @Transactional
    public AppointmentResponse changeStatus(Long id, AppointmentStatus target) {
        Appointment appointment = getAppointmentWithVehicle(id);
        AppointmentStatus current = appointment.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "An appointment cannot go from %s to %s.".formatted(current, target),
                    Map.of("currentStatus", current, "requestedStatus", target,
                            "allowedStatuses", current.allowedTargets().stream().sorted().toList()));
        }
        appointment.changeStatus(target, Instant.now(clock));
        appointmentRepository.flush();
        return withCost(appointment);
    }

    @Transactional
    public void delete(Long id) {
        Appointment appointment = getAppointment(id);
        if (!DELETABLE.contains(appointment.getStatus())) {
            throw notModifiable(appointment, "deleted");
        }
        appointmentRepository.delete(appointment);
    }

    /** Used by other features (e.g. interventions) to resolve an appointment. */
    public Appointment getAppointment(Long id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    /**
     * Loads the appointment with a shared row lock for the rest of the caller's transaction, so
     * its status cannot change while interventions of it are being modified.
     */
    @Transactional
    public Appointment lockForInterventionChanges(Long id) {
        return appointmentRepository.findByIdForShare(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private Appointment getAppointmentWithVehicle(Long id) {
        return appointmentRepository.findWithVehicleById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private AppointmentResponse withCost(Appointment appointment) {
        return appointmentMapper.toResponse(appointment, costOf(appointment.getId()));
    }

    private void assertNotInPast(Instant startAt) {
        if (startAt.isBefore(Instant.now(clock))) {
            throw new BusinessRuleException(ErrorCode.APPOINTMENT_IN_PAST, "Appointments cannot start in the past.");
        }
    }

    private static BusinessRuleException overlap(Long vehicleId) {
        return new BusinessRuleException(ErrorCode.APPOINTMENT_OVERLAP,
                "Vehicle %d already has an appointment in this interval.".formatted(vehicleId));
    }

    private static BusinessRuleException notModifiable(Appointment appointment, String action) {
        return new BusinessRuleException(ErrorCode.APPOINTMENT_NOT_MODIFIABLE,
                "A %s appointment cannot be %s.".formatted(appointment.getStatus(), action),
                Map.of("currentStatus", appointment.getStatus()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
