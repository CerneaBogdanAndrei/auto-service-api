package io.github.cerneabogdanandrei.autoservice.vehicle;

import io.github.cerneabogdanandrei.autoservice.client.Client;
import io.github.cerneabogdanandrei.autoservice.client.ClientService;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.vehicle.dto.VehicleRequest;
import io.github.cerneabogdanandrei.autoservice.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    private static final VehicleRequest REQUEST =
            new VehicleRequest(1L, " wvwzzz1jzxw000001 ", " b-123-abc ", " Volkswagen ", " Golf ", 2019);

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private ClientService clientService;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, new VehicleMapperImpl(), clientService);
    }

    @Test
    void findAllWithoutClientListsEveryVehicle() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(vehicleRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(vehicle(7L, client(1L))), pageable, 1));

        Page<VehicleResponse> page = vehicleService.findAll(null, pageable);

        assertThat(page.getContent()).singleElement()
                .satisfies(v -> assertThat(v.client()).isEqualTo(new VehicleResponse.ClientSummary(1L, "John Doe")));
    }

    @Test
    void findAllWithClientListsOnlyThatClientsVehicles() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(vehicleRepository.findByClientId(1L, pageable)).thenReturn(Page.empty(pageable));

        assertThat(vehicleService.findAll(1L, pageable)).isEmpty();
        verify(vehicleRepository, never()).findAll(pageable);
    }

    @Test
    void findByIdThrowsWhenVehicleDoesNotExist() {
        when(vehicleRepository.findWithClientById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.findById(7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vehicle with id 7 was not found");
    }

    @Test
    void createNormalizesFieldsAndAttachesClient() {
        Client owner = client(1L);
        when(vehicleRepository.existsByVin("WVWZZZ1JZXW000001")).thenReturn(false);
        when(clientService.getClient(1L)).thenReturn(owner);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.create(REQUEST);

        ArgumentCaptor<Vehicle> saved = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(saved.capture());
        assertThat(saved.getValue().getVin()).isEqualTo("WVWZZZ1JZXW000001");
        assertThat(saved.getValue().getLicensePlate()).isEqualTo("B-123-ABC");
        assertThat(saved.getValue().getMake()).isEqualTo("Volkswagen");
        assertThat(saved.getValue().getModel()).isEqualTo("Golf");
        assertThat(saved.getValue().getClient()).isSameAs(owner);
        assertThat(response.manufactureYear()).isEqualTo(2019);
    }

    @Test
    void createRejectsDuplicateVin() {
        when(vehicleRepository.existsByVin("WVWZZZ1JZXW000001")).thenReturn(true);

        assertBusinessRule(() -> vehicleService.create(REQUEST), ErrorCode.VIN_ALREADY_EXISTS);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void createForUnknownClientPropagatesNotFound() {
        when(vehicleRepository.existsByVin("WVWZZZ1JZXW000001")).thenReturn(false);
        when(clientService.getClient(1L)).thenThrow(new ResourceNotFoundException("Client", 1L));

        assertThatThrownBy(() -> vehicleService.create(REQUEST)).isInstanceOf(ResourceNotFoundException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void updateCanTransferVehicleToAnotherClient() {
        Vehicle vehicle = vehicle(7L, client(1L));
        Client newOwner = client(2L);
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByVinAndIdNot("WVWZZZ1JZXW000001", 7L)).thenReturn(false);
        when(clientService.getClient(2L)).thenReturn(newOwner);

        vehicleService.update(7L, new VehicleRequest(2L, "WVWZZZ1JZXW000001", "CJ-01-XYZ", "VW", "Passat", 2021));

        assertThat(vehicle.getClient()).isSameAs(newOwner);
        assertThat(vehicle.getModel()).isEqualTo("Passat");
        assertThat(vehicle.getLicensePlate()).isEqualTo("CJ-01-XYZ");
    }

    @Test
    void updateRejectsVinOfAnotherVehicle() {
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle(7L, client(1L))));
        when(vehicleRepository.existsByVinAndIdNot("WVWZZZ1JZXW000001", 7L)).thenReturn(true);

        assertBusinessRule(() -> vehicleService.update(7L, REQUEST), ErrorCode.VIN_ALREADY_EXISTS);
    }

    @Test
    void deleteRemovesVehicleWithoutAppointments() {
        Vehicle vehicle = vehicle(7L, client(1L));
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.hasAppointments(7L)).thenReturn(false);

        vehicleService.delete(7L);

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void vehicleWithAppointmentsCannotBeDeleted() {
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle(7L, client(1L))));
        when(vehicleRepository.hasAppointments(7L)).thenReturn(true);

        assertBusinessRule(() -> vehicleService.delete(7L), ErrorCode.VEHICLE_HAS_APPOINTMENTS);
        verify(vehicleRepository, never()).delete(any(Vehicle.class));
    }

    @Test
    void getVehicleThrowsWhenVehicleDoesNotExist() {
        when(vehicleRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getVehicle(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static void assertBusinessRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    static Client client(Long id) {
        Client client = new Client("John Doe", "+40712345678", "john@example.com");
        ReflectionTestUtils.setField(client, "id", id);
        return client;
    }

    static Vehicle vehicle(Long id, Client client) {
        try {
            Constructor<Vehicle> constructor = Vehicle.class.getDeclaredConstructor();
            Vehicle vehicle = constructor.newInstance();
            ReflectionTestUtils.setField(vehicle, "id", id);
            vehicle.setClient(client);
            vehicle.setVin("WVWZZZ1JZXW000009");
            vehicle.setLicensePlate("B-99-OLD");
            vehicle.setMake("VW");
            vehicle.setModel("Golf");
            vehicle.setManufactureYear(2015);
            return vehicle;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
