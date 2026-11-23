package io.github.cerneabogdanandrei.autoservice.client;

import io.github.cerneabogdanandrei.autoservice.client.dto.ClientRequest;
import io.github.cerneabogdanandrei.autoservice.client.dto.ClientResponse;
import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final ClientRequest REQUEST = new ClientRequest(" John Doe ", " +40 712 345 678 ", " John.Doe@Example.com ");

    @Mock
    private ClientRepository clientRepository;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, new ClientMapperImpl());
    }

    @Test
    void findAllReturnsMappedPage() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(clientRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(client(1L, "a@example.com")), pageable, 1));

        assertThat(clientService.findAll(pageable).getContent())
                .extracting(ClientResponse::email)
                .containsExactly("a@example.com");
    }

    @Test
    void findByIdThrowsWhenClientDoesNotExist() {
        when(clientRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.findById(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client with id 9 was not found");
    }

    @Test
    void createNormalizesAndSavesClient() {
        when(clientRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponse response = clientService.create(REQUEST);

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("John Doe");
        assertThat(saved.getValue().getPhone()).isEqualTo("+40 712 345 678");
        assertThat(saved.getValue().getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.email()).isEqualTo("john.doe@example.com");
    }

    @Test
    void createRejectsEmailAlreadyUsedByAnotherClient() {
        when(clientRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        assertBusinessRule(() -> clientService.create(REQUEST), ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void updateReplacesFieldsAndKeepsIdentity() {
        Client client = client(5L, "old@example.com");
        when(clientRepository.findById(5L)).thenReturn(Optional.of(client));
        when(clientRepository.existsByEmailAndIdNot("john.doe@example.com", 5L)).thenReturn(false);

        ClientResponse response = clientService.update(5L, REQUEST);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(client.getName()).isEqualTo("John Doe");
        assertThat(client.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void updateRejectsEmailOfAnotherClient() {
        when(clientRepository.findById(5L)).thenReturn(Optional.of(client(5L, "old@example.com")));
        when(clientRepository.existsByEmailAndIdNot("john.doe@example.com", 5L)).thenReturn(true);

        assertBusinessRule(() -> clientService.update(5L, REQUEST), ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void updateThrowsWhenClientDoesNotExist() {
        when(clientRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.update(5L, REQUEST)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesVehiclesWithoutHistoryThenTheClient() {
        Client client = client(5L, "a@example.com");
        when(clientRepository.findById(5L)).thenReturn(Optional.of(client));
        when(clientRepository.hasVehiclesWithAppointments(5L)).thenReturn(false);

        clientService.delete(5L);

        InOrder order = inOrder(clientRepository);
        order.verify(clientRepository).deleteVehiclesWithoutHistory(5L);
        order.verify(clientRepository).delete(client);
    }

    @Test
    void clientWhoseVehiclesHaveAppointmentsCannotBeDeleted() {
        when(clientRepository.findById(5L)).thenReturn(Optional.of(client(5L, "a@example.com")));
        when(clientRepository.hasVehiclesWithAppointments(5L)).thenReturn(true);

        assertBusinessRule(() -> clientService.delete(5L), ErrorCode.CLIENT_HAS_APPOINTMENTS);
        verify(clientRepository, never()).deleteVehiclesWithoutHistory(anyLong());
        verify(clientRepository, never()).delete(any(Client.class));
    }

    @Test
    void deleteThrowsWhenClientDoesNotExist() {
        when(clientRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.delete(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static void assertBusinessRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    static Client client(Long id, String email) {
        Client client = new Client("Client", "+40712345678", email);
        ReflectionTestUtils.setField(client, "id", id);
        return client;
    }
}
