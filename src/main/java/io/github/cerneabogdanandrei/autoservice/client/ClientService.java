package io.github.cerneabogdanandrei.autoservice.client;

import io.github.cerneabogdanandrei.autoservice.client.dto.ClientRequest;
import io.github.cerneabogdanandrei.autoservice.client.dto.ClientResponse;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private static final String RESOURCE = "Client";

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    ClientService(ClientRepository clientRepository, ClientMapper clientMapper) {
        this.clientRepository = clientRepository;
        this.clientMapper = clientMapper;
    }

    public Page<ClientResponse> findAll(Pageable pageable) {
        return clientRepository.findAll(pageable).map(clientMapper::toResponse);
    }

    public ClientResponse findById(Long id) {
        return clientMapper.toResponse(getClient(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        String email = normalizeEmail(request.email());
        if (clientRepository.existsByEmail(email)) {
            throw emailTaken(email);
        }
        Client client = clientMapper.toEntity(request);
        normalize(client, email);
        return clientMapper.toResponse(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = getClient(id);
        String email = normalizeEmail(request.email());
        if (clientRepository.existsByEmailAndIdNot(email, id)) {
            throw emailTaken(email);
        }
        clientMapper.update(request, client);
        normalize(client, email);
        clientRepository.flush();
        return clientMapper.toResponse(client);
    }

    /**
     * A client can be deleted only while none of their vehicles has ever had an appointment
     * (whatever its status), so service history is never lost. Vehicles without any history
     * are removed together with the client.
     */
    @Transactional
    public void delete(Long id) {
        Client client = getClient(id);
        if (clientRepository.hasVehiclesWithAppointments(id)) {
            throw new BusinessRuleException(ErrorCode.CLIENT_HAS_APPOINTMENTS,
                    "Client %d has vehicles with appointments and cannot be deleted.".formatted(id));
        }
        clientRepository.deleteVehiclesWithoutHistory(id);
        clientRepository.delete(client);
    }

    /** Used by other features (e.g. vehicles) to resolve the owning client. */
    public Client getClient(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private static void normalize(Client client, String email) {
        client.setName(client.getName().trim());
        client.setPhone(client.getPhone().trim());
        client.setEmail(email);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessRuleException emailTaken(String email) {
        return new BusinessRuleException(ErrorCode.EMAIL_ALREADY_EXISTS,
                "A client with email '%s' already exists.".formatted(email));
    }
}
