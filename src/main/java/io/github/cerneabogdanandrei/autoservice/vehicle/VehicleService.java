package io.github.cerneabogdanandrei.autoservice.vehicle;

import io.github.cerneabogdanandrei.autoservice.client.ClientService;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.vehicle.dto.VehicleRequest;
import io.github.cerneabogdanandrei.autoservice.vehicle.dto.VehicleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class VehicleService {

    private static final String RESOURCE = "Vehicle";

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final ClientService clientService;

    VehicleService(VehicleRepository vehicleRepository, VehicleMapper vehicleMapper, ClientService clientService) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleMapper = vehicleMapper;
        this.clientService = clientService;
    }

    public Page<VehicleResponse> findAll(Long clientId, Pageable pageable) {
        Page<Vehicle> vehicles = clientId == null
                ? vehicleRepository.findAll(pageable)
                : vehicleRepository.findByClientId(clientId, pageable);
        return vehicles.map(vehicleMapper::toResponse);
    }

    public VehicleResponse findById(Long id) {
        return vehicleMapper.toResponse(vehicleRepository.findWithClientById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id)));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String vin = normalizeVin(request.vin());
        if (vehicleRepository.existsByVin(vin)) {
            throw vinTaken(vin);
        }
        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setClient(clientService.getClient(request.clientId()));
        normalize(vehicle, vin);
        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = getVehicle(id);
        String vin = normalizeVin(request.vin());
        if (vehicleRepository.existsByVinAndIdNot(vin, id)) {
            throw vinTaken(vin);
        }
        vehicleMapper.update(request, vehicle);
        vehicle.setClient(clientService.getClient(request.clientId()));
        normalize(vehicle, vin);
        return vehicleMapper.toResponse(vehicle);
    }

    /**
     * A vehicle with any appointment, whatever its status, is part of the service history and
     * cannot be deleted.
     */
    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = getVehicle(id);
        if (vehicleRepository.hasAppointments(id)) {
            throw new BusinessRuleException(ErrorCode.VEHICLE_HAS_APPOINTMENTS,
                    "Vehicle %d has appointments and cannot be deleted.".formatted(id));
        }
        vehicleRepository.delete(vehicle);
    }

    /** Used by other features (e.g. appointments) to resolve a vehicle. */
    public Vehicle getVehicle(Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private static void normalize(Vehicle vehicle, String vin) {
        vehicle.setVin(vin);
        vehicle.setLicensePlate(vehicle.getLicensePlate().trim().toUpperCase(Locale.ROOT));
        vehicle.setMake(vehicle.getMake().trim());
        vehicle.setModel(vehicle.getModel().trim());
    }

    private static String normalizeVin(String vin) {
        return vin.trim().toUpperCase(Locale.ROOT);
    }

    private static BusinessRuleException vinTaken(String vin) {
        return new BusinessRuleException(ErrorCode.VIN_ALREADY_EXISTS,
                "A vehicle with VIN '%s' already exists.".formatted(vin));
    }
}
