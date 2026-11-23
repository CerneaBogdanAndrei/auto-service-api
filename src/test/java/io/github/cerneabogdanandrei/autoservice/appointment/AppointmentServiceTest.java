package io.github.cerneabogdanandrei.autoservice.appointment;

import io.github.cerneabogdanandrei.autoservice.appointment.dto.AppointmentRequest;
import io.github.cerneabogdanandrei.autoservice.appointment.dto.AppointmentResponse;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.common.time.BusinessCalendar;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vehicle;
import io.github.cerneabogdanandrei.autoservice.vehicle.VehicleService;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vehicles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-15T10:00:00Z");
    private static final Instant TOMORROW_9 = Instant.parse("2030-01-16T09:00:00Z");
    private static final Instant TOMORROW_11 = Instant.parse("2030-01-16T11:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private VehicleService vehicleService;

    private AppointmentService service;
    private final Vehicle vehicle = Vehicles.vehicle(1L);

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, new AppointmentMapperImpl(), vehicleService,
                new BusinessCalendar(ZoneId.of("Europe/Bucharest")), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------ queries

    @Test
    void findAllRejectsRangeEndingBeforeItStarts() {
        assertThatThrownBy(() -> service.findAll(LocalDate.of(2030, 2, 1), LocalDate.of(2030, 1, 1), null, null,
                PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllMapsTheFilteredPage() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(appointmentRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(appointment(5L, AppointmentStatus.SCHEDULED))));

        List<AppointmentResponse> content = service.findAll(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31),
                AppointmentStatus.SCHEDULED, 1L, pageable).getContent();

        assertThat(content).singleElement().satisfies(response -> {
            assertThat(response.vehicle().vin()).isEqualTo(vehicle.getVin());
            assertThat(response.cost()).isNull();
        });
    }

    @Test
    void findByIdIncludesTheCostOfAllInterventionsAndParts() {
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment(5L, AppointmentStatus.COMPLETED)));
        when(appointmentRepository.findLaborCosts(5L)).thenReturn(List.of(new BigDecimal("150.00"), new BigDecimal("100.50")));
        when(appointmentRepository.findPartLines(5L)).thenReturn(List.of(
                new Line(2, new BigDecimal("49.90")), new Line(1, new BigDecimal("35.00"))));

        AppointmentResponse response = service.findById(5L);

        assertThat(response.cost()).isEqualTo(new AppointmentCost(
                new BigDecimal("250.50"), new BigDecimal("134.80"), new BigDecimal("385.30")));
    }

    @Test
    void findByIdThrowsWhenAppointmentDoesNotExist() {
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Appointment with id 5 was not found");
    }

    // ------------------------------------------------------------------ create

    @Test
    void createSchedulesAnAppointment() {
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);
        when(appointmentRepository.existsOverlapping(1L, TOMORROW_9, TOMORROW_11)).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        AppointmentResponse response = service.create(request(TOMORROW_9, TOMORROW_11, "  Oil change  "));

        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(saved.getValue().getVehicle()).isSameAs(vehicle);
        assertThat(saved.getValue().getNotes()).isEqualTo("Oil change");
        assertThat(response.startAt()).isEqualTo(TOMORROW_9);
        assertThat(response.cost().total()).isEqualByComparingTo("0.00");
    }

    @Test
    void createRejectsAppointmentStartingInThePast() {
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);

        assertBusinessRule(() -> service.create(request(NOW.minusSeconds(1), TOMORROW_11, null)), ErrorCode.APPOINTMENT_IN_PAST);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createAcceptsAppointmentStartingRightNow() {
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        assertThat(service.create(request(NOW, TOMORROW_11, null)).status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void createRejectsOverlappingAppointment() {
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);
        when(appointmentRepository.existsOverlapping(1L, TOMORROW_9, TOMORROW_11)).thenReturn(true);

        assertBusinessRule(() -> service.create(request(TOMORROW_9, TOMORROW_11, null)), ErrorCode.APPOINTMENT_OVERLAP);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createForUnknownVehiclePropagatesNotFound() {
        when(vehicleService.getVehicle(1L)).thenThrow(new ResourceNotFoundException("Vehicle", 1L));

        assertThatThrownBy(() -> service.create(request(TOMORROW_9, TOMORROW_11, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateReschedulesScheduledAppointment() {
        Appointment appointment = appointment(5L, AppointmentStatus.SCHEDULED);
        Instant newStart = TOMORROW_9.plus(Duration.ofDays(1));
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment));
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);
        when(appointmentRepository.existsOverlappingExcluding(1L, newStart, newStart.plus(Duration.ofHours(1)), 5L))
                .thenReturn(false);

        service.update(5L, request(newStart, newStart.plus(Duration.ofHours(1)), "Moved"));

        assertThat(appointment.getStartAt()).isEqualTo(newStart);
        assertThat(appointment.getNotes()).isEqualTo("Moved");
        verify(appointmentRepository).flush();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void onlyScheduledAppointmentsCanBeRescheduled(AppointmentStatus status) {
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment(5L, status)));

        assertBusinessRule(() -> service.update(5L, request(TOMORROW_9, TOMORROW_11, null)), ErrorCode.APPOINTMENT_NOT_MODIFIABLE);
    }

    @Test
    void updateRejectsMovingIntoThePast() {
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment(5L, AppointmentStatus.SCHEDULED)));
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);

        assertBusinessRule(() -> service.update(5L, request(NOW.minusSeconds(60), TOMORROW_11, null)),
                ErrorCode.APPOINTMENT_IN_PAST);
    }

    @Test
    void updateRejectsOverlapWithAnotherAppointment() {
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment(5L, AppointmentStatus.SCHEDULED)));
        when(vehicleService.getVehicle(1L)).thenReturn(vehicle);
        when(appointmentRepository.existsOverlappingExcluding(1L, TOMORROW_9, TOMORROW_11, 5L)).thenReturn(true);

        assertBusinessRule(() -> service.update(5L, request(TOMORROW_9, TOMORROW_11, null)), ErrorCode.APPOINTMENT_OVERLAP);
    }

    // ------------------------------------------------------------------ status

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"SCHEDULED, IN_PROGRESS", "SCHEDULED, CANCELLED", "IN_PROGRESS, COMPLETED"})
    void allowedTransitionsAreApplied(AppointmentStatus from, AppointmentStatus to) {
        Appointment appointment = appointment(5L, from);
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = service.changeStatus(5L, to);

        assertThat(response.status()).isEqualTo(to);
        assertThat(appointment.getCompletedAt()).isEqualTo(to == AppointmentStatus.COMPLETED ? NOW : null);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "SCHEDULED, SCHEDULED", "SCHEDULED, COMPLETED",
            "IN_PROGRESS, SCHEDULED", "IN_PROGRESS, IN_PROGRESS", "IN_PROGRESS, CANCELLED",
            "COMPLETED, SCHEDULED", "COMPLETED, IN_PROGRESS", "COMPLETED, COMPLETED", "COMPLETED, CANCELLED",
            "CANCELLED, SCHEDULED", "CANCELLED, IN_PROGRESS", "CANCELLED, COMPLETED", "CANCELLED, CANCELLED"
    })
    void forbiddenTransitionsAreRejectedWithoutChangingTheAppointment(AppointmentStatus from, AppointmentStatus to) {
        Appointment appointment = appointment(5L, from);
        when(appointmentRepository.findWithVehicleById(5L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.changeStatus(5L, to))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                    assertThat(ex.getProperties()).containsEntry("currentStatus", from).containsEntry("requestedStatus", to);
                });
        assertThat(appointment.getStatus()).isEqualTo(from);
    }

    @Test
    void entityRefusesTransitionsThatBypassTheService() {
        Appointment appointment = appointment(5L, AppointmentStatus.COMPLETED);

        assertThatThrownBy(() -> appointment.changeStatus(AppointmentStatus.SCHEDULED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ delete

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "CANCELLED"})
    void scheduledOrCancelledAppointmentCanBeDeleted(AppointmentStatus status) {
        Appointment appointment = appointment(5L, status);
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appointment));

        service.delete(5L);

        verify(appointmentRepository).delete(appointment);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    void appointmentWithWorkCannotBeDeleted(AppointmentStatus status) {
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appointment(5L, status)));

        assertBusinessRule(() -> service.delete(5L), ErrorCode.APPOINTMENT_NOT_MODIFIABLE);
        verify(appointmentRepository, never()).delete(any(Appointment.class));
    }

    @Test
    void getAppointmentThrowsWhenAppointmentDoesNotExist() {
        when(appointmentRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAppointment(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ helpers

    record Line(Integer getQuantity, BigDecimal getUnitPrice) implements AppointmentRepository.PartLineView {
    }

    private static AppointmentRequest request(Instant start, Instant end, String notes) {
        return new AppointmentRequest(1L, start.atOffset(ZoneOffset.ofHours(2)), end.atOffset(ZoneOffset.ofHours(2)), notes);
    }

    private Appointment appointment(Long id, AppointmentStatus status) {
        Appointment appointment = withId(new Appointment(vehicle, TOMORROW_9, TOMORROW_11, null), id);
        ReflectionTestUtils.setField(appointment, "status", status);
        return appointment;
    }

    private static Appointment withId(Appointment appointment, Long id) {
        ReflectionTestUtils.setField(appointment, "id", id);
        return appointment;
    }

    private static void assertBusinessRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
}
