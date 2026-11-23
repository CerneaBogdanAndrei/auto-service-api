package io.github.cerneabogdanandrei.autoservice.intervention;

import io.github.cerneabogdanandrei.autoservice.appointment.Appointment;
import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentService;
import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentStatus;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionCreateRequest;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionResponse;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionSummaryResponse;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionUpdateRequest;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.PartUsageRequest;
import io.github.cerneabogdanandrei.autoservice.part.Part;
import io.github.cerneabogdanandrei.autoservice.part.PartService;
import io.github.cerneabogdanandrei.autoservice.user.User;
import io.github.cerneabogdanandrei.autoservice.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Interventions and the parts they consume.
 *
 * <p>Rules:
 * <ul>
 *   <li>interventions (and their parts) change only while the appointment is IN_PROGRESS;</li>
 *   <li>the assignee is an enabled EMPLOYEE; employees only see and change their own
 *       interventions and cannot reassign them;</li>
 *   <li>using a part takes it from stock; if any requested part lacks stock, nothing changes;
 *       removing a part or deleting an intervention puts the parts back.</li>
 * </ul>
 *
 * <p>Locking order, identical in every operation so concurrent requests cannot deadlock:
 * appointment (shared) → intervention (exclusive) → parts (exclusive, by id).
 */
@Service
@Transactional(readOnly = true)
public class InterventionService {

    private static final String RESOURCE = "Intervention";

    private final InterventionRepository interventionRepository;
    private final InterventionMapper interventionMapper;
    private final AppointmentService appointmentService;
    private final PartService partService;
    private final UserService userService;

    InterventionService(InterventionRepository interventionRepository, InterventionMapper interventionMapper,
                        AppointmentService appointmentService, PartService partService, UserService userService) {
        this.interventionRepository = interventionRepository;
        this.interventionMapper = interventionMapper;
        this.appointmentService = appointmentService;
        this.partService = partService;
        this.userService = userService;
    }

    /** Employees always get only their own interventions, whatever assignee filter they send. */
    public Page<InterventionSummaryResponse> findAll(Long appointmentId, Long assigneeId, AuthenticatedUser user,
                                                     Pageable pageable) {
        Long effectiveAssignee = user.hasRole(Role.EMPLOYEE) ? user.id() : assigneeId;
        Specification<Intervention> filter = Specification.allOf(
                InterventionSpecifications.forAppointment(appointmentId),
                InterventionSpecifications.assignedTo(effectiveAssignee));
        return interventionRepository.findAll(filter, pageable).map(interventionMapper::toSummary);
    }

    public InterventionResponse findById(Long id, AuthenticatedUser user) {
        Intervention intervention = interventionRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        assertCanAccess(intervention, user);
        return interventionMapper.toResponse(intervention);
    }

    @Transactional
    public InterventionResponse create(InterventionCreateRequest request) {
        Appointment appointment = appointmentService.lockForInterventionChanges(request.appointmentId());
        assertInProgress(appointment);
        User assignee = validAssignee(request.assignedUserId());
        Intervention intervention = new Intervention(appointment, assignee, request.description().trim(),
                request.laborCost());
        takeFromStock(intervention, request.partsOrEmpty());
        return interventionMapper.toResponse(interventionRepository.save(intervention));
    }

    @Transactional
    public InterventionResponse update(Long id, InterventionUpdateRequest request, AuthenticatedUser user) {
        Intervention intervention = lockForChange(id, user);
        Long newAssignee = request.assignedUserId();
        if (newAssignee != null && !intervention.isAssignedTo(newAssignee)) {
            if (user.hasRole(Role.EMPLOYEE)) {
                throw new AccessDeniedException("Employees cannot reassign interventions");
            }
            intervention.setAssignedUser(validAssignee(newAssignee));
        }
        intervention.setDescription(request.description().trim());
        intervention.setLaborCost(request.laborCost());
        interventionRepository.flush();
        return interventionMapper.toResponse(intervention);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser user) {
        Intervention intervention = lockForChange(id, user);
        List<InterventionPart> lines = List.copyOf(intervention.getParts());
        returnToStock(lines);
        interventionRepository.delete(intervention);
    }

    @Transactional
    public InterventionResponse addPart(Long id, PartUsageRequest request, AuthenticatedUser user) {
        Intervention intervention = lockForChange(id, user);
        takeFromStock(intervention, List.of(request));
        interventionRepository.flush();
        return interventionMapper.toResponse(intervention);
    }

    @Transactional
    public InterventionResponse removePart(Long id, Long partId, AuthenticatedUser user) {
        Intervention intervention = lockForChange(id, user);
        InterventionPart line = intervention.findLine(partId).orElseThrow(() -> new ResourceNotFoundException(
                "Part %d on intervention".formatted(partId), id));
        returnToStock(List.of(line));
        intervention.removeLine(line);
        interventionRepository.flush();
        return interventionMapper.toResponse(intervention);
    }

    // ------------------------------------------------------------------ rules

    /**
     * Locks in the documented order and checks access before state, so an employee probing
     * someone else's intervention learns nothing about it.
     */
    private Intervention lockForChange(Long id, AuthenticatedUser user) {
        Long appointmentId = interventionRepository.findAppointmentIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        Appointment appointment = appointmentService.lockForInterventionChanges(appointmentId);
        Intervention intervention = interventionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        assertCanAccess(intervention, user);
        assertInProgress(appointment);
        return intervention;
    }

    private static void assertCanAccess(Intervention intervention, AuthenticatedUser user) {
        if (user.hasRole(Role.EMPLOYEE) && !intervention.isAssignedTo(user.id())) {
            throw new AccessDeniedException("Intervention is assigned to another employee");
        }
    }

    private static void assertInProgress(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new BusinessRuleException(ErrorCode.APPOINTMENT_NOT_IN_PROGRESS,
                    "Interventions can only be changed while the appointment is IN_PROGRESS (it is %s)."
                            .formatted(appointment.getStatus()),
                    Map.of("appointmentStatus", appointment.getStatus()));
        }
    }

    private User validAssignee(Long userId) {
        User user = userService.getUser(userId);
        if (user.getRole() != Role.EMPLOYEE || !user.isEnabled()) {
            throw new BusinessRuleException(ErrorCode.INVALID_ASSIGNEE,
                    "Interventions can only be assigned to an active employee; user %d is not one.".formatted(userId));
        }
        return user;
    }

    /**
     * Takes every requested part from stock, or none: all quantities are checked against the
     * locked rows before any stock is changed, and the transaction covers the rest.
     */
    private void takeFromStock(Intervention intervention, List<PartUsageRequest> usages) {
        if (usages.isEmpty()) {
            return;
        }
        SortedMap<Long, Integer> requested = new TreeMap<>();
        usages.forEach(usage -> requested.merge(usage.partId(), usage.quantity(), Integer::sum));
        List<Part> parts = partService.lockForUpdate(requested.keySet());
        for (Part part : parts) {
            int quantity = requested.get(part.getId());
            if (part.getStock() < quantity) {
                throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK,
                        "Insufficient stock for part %s: requested %d, available %d."
                                .formatted(part.getCode(), quantity, part.getStock()),
                        Map.of("partId", part.getId(), "requested", quantity, "available", part.getStock()));
            }
        }
        for (Part part : parts) {
            int quantity = requested.get(part.getId());
            part.takeFromStock(quantity);
            intervention.addPart(part, quantity);
        }
    }

    private void returnToStock(List<InterventionPart> lines) {
        if (lines.isEmpty()) {
            return;
        }
        SortedMap<Long, Integer> returned = new TreeMap<>();
        lines.forEach(line -> returned.merge(line.getPart().getId(), line.getQuantity(), Integer::sum));
        for (Part part : partService.lockForUpdate(returned.keySet())) {
            part.returnToStock(returned.get(part.getId()));
        }
    }
}
