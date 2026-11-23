package io.github.cerneabogdanandrei.autoservice.part;

import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.part.dto.PartRequest;
import io.github.cerneabogdanandrei.autoservice.part.dto.PartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartServiceTest {

    private static final PartRequest REQUEST = new PartRequest(" flt-001 ", " Oil filter ", new BigDecimal("49.9"), 10);

    @Mock
    private PartRepository partRepository;

    private PartService partService;

    @BeforeEach
    void setUp() {
        partService = new PartService(partRepository, new PartMapperImpl());
    }

    @Test
    void findAllWithoutThresholdListsEveryPart() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(partRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(Parts.part(1L, "FLT-001", "49.90", 10))));

        assertThat(partService.findAll(null, pageable).getContent()).extracting(PartResponse::code).containsExactly("FLT-001");
        verify(partRepository, never()).findByStockLessThan(any(Integer.class), any());
    }

    @Test
    void findAllWithThresholdListsOnlyLowStock() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(partRepository.findByStockLessThan(5, pageable)).thenReturn(Page.empty(pageable));

        assertThat(partService.findAll(5, pageable)).isEmpty();
    }

    @Test
    void findByIdThrowsWhenPartDoesNotExist() {
        when(partRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partService.findById(3L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createNormalizesCodeNameAndPriceScale() {
        when(partRepository.existsByCode("FLT-001")).thenReturn(false);
        when(partRepository.save(any(Part.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartResponse response = partService.create(REQUEST);

        ArgumentCaptor<Part> saved = ArgumentCaptor.forClass(Part.class);
        verify(partRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("FLT-001");
        assertThat(saved.getValue().getName()).isEqualTo("Oil filter");
        assertThat(saved.getValue().getUnitPrice()).isEqualByComparingTo("49.90").hasScaleOf(2);
        assertThat(response.stock()).isEqualTo(10);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(partRepository.existsByCode("FLT-001")).thenReturn(true);

        assertBusinessRule(() -> partService.create(REQUEST), ErrorCode.PART_CODE_ALREADY_EXISTS);
        verify(partRepository, never()).save(any());
    }

    @Test
    void updateReplacesFieldsIncludingStock() {
        Part part = Parts.part(3L, "OLD-1", "10.00", 1);
        when(partRepository.findById(3L)).thenReturn(Optional.of(part));
        when(partRepository.existsByCodeAndIdNot("FLT-001", 3L)).thenReturn(false);

        partService.update(3L, REQUEST);

        assertThat(part.getCode()).isEqualTo("FLT-001");
        assertThat(part.getStock()).isEqualTo(10);
        assertThat(part.getUnitPrice()).isEqualByComparingTo("49.90");
    }

    @Test
    void updateRejectsCodeOfAnotherPart() {
        when(partRepository.findById(3L)).thenReturn(Optional.of(Parts.part(3L, "OLD-1", "10.00", 1)));
        when(partRepository.existsByCodeAndIdNot("FLT-001", 3L)).thenReturn(true);

        assertBusinessRule(() -> partService.update(3L, REQUEST), ErrorCode.PART_CODE_ALREADY_EXISTS);
    }

    @Test
    void deleteRemovesUnusedPart() {
        Part part = Parts.part(3L, "FLT-001", "49.90", 1);
        when(partRepository.findById(3L)).thenReturn(Optional.of(part));
        when(partRepository.isUsedInInterventions(3L)).thenReturn(false);

        partService.delete(3L);

        verify(partRepository).delete(part);
    }

    @Test
    void partUsedByInterventionsCannotBeDeleted() {
        when(partRepository.findById(3L)).thenReturn(Optional.of(Parts.part(3L, "FLT-001", "49.90", 1)));
        when(partRepository.isUsedInInterventions(3L)).thenReturn(true);

        assertBusinessRule(() -> partService.delete(3L), ErrorCode.PART_IN_USE);
        verify(partRepository, never()).delete(any(Part.class));
    }

    @Test
    void lockForUpdateReturnsLockedParts() {
        List<Part> parts = List.of(Parts.part(1L, "A", "1.00", 1), Parts.part(2L, "B", "2.00", 2));
        when(partRepository.findAllByIdForUpdate(List.of(2L, 1L))).thenReturn(parts);

        assertThat(partService.lockForUpdate(List.of(2L, 1L))).isSameAs(parts);
    }

    @Test
    void lockForUpdateReportsTheFirstMissingPart() {
        when(partRepository.findAllByIdForUpdate(List.of(1L, 9L))).thenReturn(List.of(Parts.part(1L, "A", "1.00", 1)));

        assertThatThrownBy(() -> partService.lockForUpdate(List.of(1L, 9L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Part with id 9 was not found");
    }

    private static void assertBusinessRule(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
}
