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
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionUpdateRequest;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.PartUsageRequest;
import io.github.cerneabogdanandrei.autoservice.part.Part;
import io.github.cerneabogdanandrei.autoservice.part.PartService;
import io.github.cerneabogdanandrei.autoservice.part.Parts;
import io.github.cerneabogdanandrei.autoservice.user.User;
import io.github.cerneabogdanandrei.autoservice.user.UserService;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vehicles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterventionServiceTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(2L, "owner", Role.OWNER);
    private static final AuthenticatedUser EMPLOYEE = new AuthenticatedUser(3L, "employee", Role.EMPLOYEE);
    private static final AuthenticatedUser OTHER_EMPLOYEE = new AuthenticatedUser(4L, "mechanic", Role.EMPLOYEE);

    @Mock
    private InterventionRepository interventionRepository;
    @Mock
    private AppointmentService appointmentService;
    @Mock
    private PartService partService;
    @Mock
    private UserService userService;

    private InterventionService service;

    private final User employee = user(3L, Role.EMPLOYEE, true);
    private final User otherEmployee = user(4L, Role.EMPLOYEE, true);
    private final Appointment inProgress = appointment(10L, AppointmentStatus.IN_PROGRESS);
    private final Part filter = Parts.part(1L, "FLT-001", "49.90", 10);
    private final Part oil = Parts.part(2L, "OIL-5W30", "35.00", 3);

    @BeforeEach
    void setUp() {
        service = new InterventionService(interventionRepository, new InterventionMapperImpl(), appointmentService,
                partService, userService);
    }

    // ------------------------------------------------------------------ create

    @Test
    void createTakesEveryRequestedPartFromStockAndRecordsTheirPrices() {
        givenCreatable();
        when(partService.lockForUpdate(lockedInOrder(1L, 2L))).thenReturn(List.of(filter, oil));

        InterventionResponse response = service.create(createRequest(
                new PartUsageRequest(1L, 2), new PartUsageRequest(2L, 1), new PartUsageRequest(1L, 1)));

        assertThat(filter.getStock()).isEqualTo(7);
        assertThat(oil.getStock()).isEqualTo(2);
        assertThat(response.parts()).extracting(InterventionResponse.PartLine::code, InterventionResponse.PartLine::quantity)
                .containsExactly(tuple("FLT-001", 3), tuple("OIL-5W30", 1));
        assertThat(response.partsCost()).isEqualTo(new BigDecimal("184.70"));
        assertThat(response.total()).isEqualTo(new BigDecimal("334.70"));
        assertThat(response.assignee().id()).isEqualTo(3L);
    }

    @Test
    void createWithoutPartsDoesNotTouchStock() {
        givenCreatable();

        InterventionResponse response = service.create(createRequest());

        assertThat(response.parts()).isEmpty();
        assertThat(response.total()).isEqualTo(new BigDecimal("150.00"));
        verifyNoInteractions(partService);
    }

    @Test
    void insufficientStockForAnyPartChangesNothing() {
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(inProgress);
        when(userService.getUser(3L)).thenReturn(employee);
        when(partService.lockForUpdate(lockedInOrder(1L, 2L))).thenReturn(List.of(filter, oil));

        assertThatThrownBy(() -> service.create(createRequest(new PartUsageRequest(1L, 2), new PartUsageRequest(2L, 5))))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(ex.getMessage()).isEqualTo("Insufficient stock for part OIL-5W30: requested 5, available 3.");
                    assertThat(ex.getProperties()).containsEntry("partId", 2L).containsEntry("requested", 5)
                            .containsEntry("available", 3);
                });
        // the part that did have enough stock was not touched either
        assertThat(filter.getStock()).isEqualTo(10);
        assertThat(oil.getStock()).isEqualTo(3);
        verify(interventionRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "COMPLETED", "CANCELLED"})
    void interventionsCanOnlyBeCreatedWhileTheAppointmentIsInProgress(AppointmentStatus status) {
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(appointment(10L, status));

        assertBusinessRule(() -> service.create(createRequest(new PartUsageRequest(1L, 1))), ErrorCode.APPOINTMENT_NOT_IN_PROGRESS);
        verifyNoInteractions(partService);
        verify(interventionRepository, never()).save(any());
    }

    @Test
    void assigneeMustBeAnEmployee() {
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(inProgress);
        when(userService.getUser(3L)).thenReturn(user(3L, Role.OWNER, true));

        assertBusinessRule(() -> service.create(createRequest()), ErrorCode.INVALID_ASSIGNEE);
    }

    @Test
    void assigneeMustBeEnabled() {
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(inProgress);
        when(userService.getUser(3L)).thenReturn(user(3L, Role.EMPLOYEE, false));

        assertBusinessRule(() -> service.create(createRequest()), ErrorCode.INVALID_ASSIGNEE);
    }

    @Test
    void unknownPartIsReported() {
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(inProgress);
        when(userService.getUser(3L)).thenReturn(employee);
        when(partService.lockForUpdate(any())).thenThrow(new ResourceNotFoundException("Part", 9L));

        assertThatThrownBy(() -> service.create(createRequest(new PartUsageRequest(9L, 1))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(interventionRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ read

    @Test
    @SuppressWarnings("unchecked")
    void findAllMapsSummaries() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(interventionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(intervention(5L, employee))));

        assertThat(service.findAll(null, 4L, EMPLOYEE, pageable).getContent())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.appointmentId()).isEqualTo(10L);
                    assertThat(summary.assignee().username()).isEqualTo("user3");
                });
    }

    @Test
    void ownerAndAssignedEmployeeCanReadAnIntervention() {
        when(interventionRepository.findDetailedById(5L)).thenReturn(Optional.of(intervention(5L, employee)));

        assertThat(service.findById(5L, OWNER).id()).isEqualTo(5L);
        assertThat(service.findById(5L, EMPLOYEE).id()).isEqualTo(5L);
    }

    @Test
    void employeeCannotReadSomeoneElsesIntervention() {
        when(interventionRepository.findDetailedById(5L)).thenReturn(Optional.of(intervention(5L, employee)));

        assertThatThrownBy(() -> service.findById(5L, OTHER_EMPLOYEE)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findByIdThrowsWhenInterventionDoesNotExist() {
        when(interventionRepository.findDetailedById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(5L, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Intervention with id 5 was not found");
    }

    // ------------------------------------------------------------------ update

    @Test
    void ownerCanReassignAndEditAnIntervention() {
        Intervention intervention = givenLocked(intervention(5L, employee), inProgress);
        when(userService.getUser(4L)).thenReturn(otherEmployee);

        InterventionResponse response = service.update(5L,
                new InterventionUpdateRequest(4L, "  Brake pads replaced  ", new BigDecimal("220.00")), OWNER);

        assertThat(intervention.getAssignedUser()).isSameAs(otherEmployee);
        assertThat(intervention.getDescription()).isEqualTo("Brake pads replaced");
        assertThat(response.laborCost()).isEqualByComparingTo("220.00");
        verify(interventionRepository).flush();
    }

    @Test
    void assignedEmployeeCanEditDescriptionAndLabour() {
        Intervention intervention = givenLocked(intervention(5L, employee), inProgress);

        service.update(5L, new InterventionUpdateRequest(3L, "Done", new BigDecimal("80.00")), EMPLOYEE);

        assertThat(intervention.getDescription()).isEqualTo("Done");
        assertThat(intervention.getAssignedUser()).isSameAs(employee);
        verifyNoInteractions(userService);
    }

    @Test
    void employeeCannotReassignTheirIntervention() {
        Intervention intervention = givenLocked(intervention(5L, employee), inProgress);

        assertThatThrownBy(() -> service.update(5L, new InterventionUpdateRequest(4L, "Done", BigDecimal.TEN), EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(intervention.getAssignedUser()).isSameAs(employee);
    }

    @Test
    void employeeIsDeniedBeforeLearningTheStateOfSomeoneElsesIntervention() {
        givenLocked(intervention(5L, employee), appointment(10L, AppointmentStatus.COMPLETED));

        assertThatThrownBy(() -> service.update(5L, new InterventionUpdateRequest(null, "x", BigDecimal.ONE), OTHER_EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void completedWorkCannotBeEdited() {
        givenLocked(intervention(5L, employee), appointment(10L, AppointmentStatus.COMPLETED));

        assertBusinessRule(() -> service.update(5L, new InterventionUpdateRequest(null, "x", BigDecimal.ONE), OWNER),
                ErrorCode.APPOINTMENT_NOT_IN_PROGRESS);
    }

    @Test
    void updatingAMissingInterventionThrows() {
        when(interventionRepository.findAppointmentIdById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new InterventionUpdateRequest(null, "x", BigDecimal.ONE), OWNER))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(appointmentService);
    }

    // ------------------------------------------------------------------ parts

    @Test
    void addingAPartCreatesALineAtTheCurrentPrice() {
        Intervention intervention = givenLocked(intervention(5L, employee), inProgress);
        when(partService.lockForUpdate(lockedInOrder(1L))).thenReturn(List.of(filter));

        InterventionResponse response = service.addPart(5L, new PartUsageRequest(1L, 2), EMPLOYEE);

        assertThat(filter.getStock()).isEqualTo(8);
        assertThat(intervention.findLine(1L)).hasValueSatisfying(line -> {
            assertThat(line.getQuantity()).isEqualTo(2);
            assertThat(line.getUnitPrice()).isEqualByComparingTo("49.90");
        });
        assertThat(response.total()).isEqualTo(new BigDecimal("249.80"));
    }

    @Test
    void addingAPartAgainKeepsThePriceRecordedTheFirstTime() {
        filter.setUnitPrice(new BigDecimal("45.00"));
        Intervention intervention = intervention(5L, employee);
        intervention.addPart(filter, 1);
        filter.setUnitPrice(new BigDecimal("49.90"));
        givenLocked(intervention, inProgress);
        when(partService.lockForUpdate(lockedInOrder(1L))).thenReturn(List.of(filter));

        service.addPart(5L, new PartUsageRequest(1L, 2), OWNER);

        assertThat(intervention.getParts()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualTo(3);
            assertThat(line.getUnitPrice()).isEqualByComparingTo("45.00");
        });
    }

    @Test
    void addingMorePartsThanInStockIsRejected() {
        Intervention intervention = givenLocked(intervention(5L, employee), inProgress);
        when(partService.lockForUpdate(lockedInOrder(2L))).thenReturn(List.of(oil));

        assertBusinessRule(() -> service.addPart(5L, new PartUsageRequest(2L, 4), EMPLOYEE), ErrorCode.INSUFFICIENT_STOCK);
        assertThat(oil.getStock()).isEqualTo(3);
        assertThat(intervention.getParts()).isEmpty();
    }

    @Test
    void employeeCannotUsePartsOnSomeoneElsesIntervention() {
        givenLocked(intervention(5L, employee), inProgress);

        assertThatThrownBy(() -> service.addPart(5L, new PartUsageRequest(1L, 1), OTHER_EMPLOYEE))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(partService);
    }

    @Test
    void removingAPartReturnsItToStock() {
        Intervention intervention = intervention(5L, employee);
        intervention.addPart(filter, 3);
        filter.takeFromStock(3);
        givenLocked(intervention, inProgress);
        when(partService.lockForUpdate(lockedInOrder(1L))).thenReturn(List.of(filter));

        InterventionResponse response = service.removePart(5L, 1L, EMPLOYEE);

        assertThat(filter.getStock()).isEqualTo(10);
        assertThat(response.parts()).isEmpty();
    }

    @Test
    void removingAPartThatIsNotOnTheInterventionThrows() {
        givenLocked(intervention(5L, employee), inProgress);

        assertThatThrownBy(() -> service.removePart(5L, 1L, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Part 1 on intervention with id 5 was not found");
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletingAnInterventionReturnsAllItsPartsToStock() {
        Intervention intervention = intervention(5L, employee);
        intervention.addPart(filter, 2);
        intervention.addPart(oil, 1);
        filter.takeFromStock(2);
        oil.takeFromStock(1);
        givenLocked(intervention, inProgress);
        when(partService.lockForUpdate(lockedInOrder(1L, 2L))).thenReturn(List.of(filter, oil));

        service.delete(5L, OWNER);

        assertThat(filter.getStock()).isEqualTo(10);
        assertThat(oil.getStock()).isEqualTo(3);
        verify(interventionRepository).delete(intervention);
    }

    @Test
    void completedWorkCannotBeDeleted() {
        givenLocked(intervention(5L, employee), appointment(10L, AppointmentStatus.COMPLETED));

        assertBusinessRule(() -> service.delete(5L, OWNER), ErrorCode.APPOINTMENT_NOT_IN_PROGRESS);
        verify(interventionRepository, never()).delete(any(Intervention.class));
    }

    // ------------------------------------------------------------------ helpers

    private void givenCreatable() {
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(inProgress);
        when(userService.getUser(3L)).thenReturn(employee);
        when(interventionRepository.save(any(Intervention.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Intervention givenLocked(Intervention intervention, Appointment appointment) {
        when(interventionRepository.findAppointmentIdById(intervention.getId())).thenReturn(Optional.of(10L));
        when(appointmentService.lockForInterventionChanges(10L)).thenReturn(appointment);
        when(interventionRepository.findByIdForUpdate(intervention.getId())).thenReturn(Optional.of(intervention));
        return intervention;
    }

    /** Parts must be locked in ascending id order (deadlock prevention). */
    private static Collection<Long> lockedInOrder(Long... ids) {
        return argThat(actual -> actual != null && List.copyOf(actual).equals(List.of(ids)));
    }

    private static InterventionCreateRequest createRequest(PartUsageRequest... parts) {
        return new InterventionCreateRequest(10L, 3L, " Oil change ", new BigDecimal("150.00"), List.of(parts));
    }

    private Intervention intervention(Long id, User assignee) {
        Intervention intervention = new Intervention(inProgress, assignee, "Oil change", new BigDecimal("150.00"));
        ReflectionTestUtils.setField(intervention, "id", id);
        return intervention;
    }

    private static Appointment appointment(Long id, AppointmentStatus status) {
        Instant start = Instant.parse("2030-01-15T08:00:00Z");
        Appointment appointment = new Appointment(Vehicles.vehicle(1L), start, start.plusSeconds(7200), null);
        ReflectionTestUtils.setField(appointment, "id", id);
        ReflectionTestUtils.setField(appointment, "status", status);
        return appointment;
    }

    private static User user(Long id, Role role, boolean enabled) {
        User user = new User("user" + id, "$2a$10$hash", "User " + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        user.setEnabled(enabled);
        return user;
    }

    private static void assertBusinessRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
}
