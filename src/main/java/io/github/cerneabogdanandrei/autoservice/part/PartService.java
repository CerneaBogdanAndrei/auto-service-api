package io.github.cerneabogdanandrei.autoservice.part;

import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.part.dto.PartRequest;
import io.github.cerneabogdanandrei.autoservice.part.dto.PartResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Parts inventory. Stock is changed directly here only by catalogue edits; consumption by
 * interventions goes through {@link #lockForUpdate(Collection)}.
 */
@Service
@Transactional(readOnly = true)
public class PartService {

    private static final String RESOURCE = "Part";

    private final PartRepository partRepository;
    private final PartMapper partMapper;

    PartService(PartRepository partRepository, PartMapper partMapper) {
        this.partRepository = partRepository;
        this.partMapper = partMapper;
    }

    /**
     * @param stockBelow when set, only parts with stock strictly below this threshold
     */
    public Page<PartResponse> findAll(Integer stockBelow, Pageable pageable) {
        Page<Part> parts = stockBelow == null
                ? partRepository.findAll(pageable)
                : partRepository.findByStockLessThan(stockBelow, pageable);
        return parts.map(partMapper::toResponse);
    }

    public PartResponse findById(Long id) {
        return partMapper.toResponse(getPart(id));
    }

    @Transactional
    public PartResponse create(PartRequest request) {
        String code = normalizeCode(request.code());
        if (partRepository.existsByCode(code)) {
            throw codeTaken(code);
        }
        Part part = partMapper.toEntity(request);
        normalize(part, code);
        return partMapper.toResponse(partRepository.save(part));
    }

    @Transactional
    public PartResponse update(Long id, PartRequest request) {
        Part part = getPart(id);
        String code = normalizeCode(request.code());
        if (partRepository.existsByCodeAndIdNot(code, id)) {
            throw codeTaken(code);
        }
        partMapper.update(request, part);
        normalize(part, code);
        return partMapper.toResponse(part);
    }

    /**
     * Parts used by any intervention are part of the cost history and cannot be deleted.
     */
    @Transactional
    public void delete(Long id) {
        Part part = getPart(id);
        if (partRepository.isUsedInInterventions(id)) {
            throw new BusinessRuleException(ErrorCode.PART_IN_USE,
                    "Part %d is used by interventions and cannot be deleted.".formatted(id));
        }
        partRepository.delete(part);
    }

    /**
     * Loads and locks the given parts (in id order) for a stock change within the caller's
     * transaction.
     *
     * @throws ResourceNotFoundException if any of the ids does not exist
     */
    @Transactional
    public List<Part> lockForUpdate(Collection<Long> ids) {
        List<Part> parts = partRepository.findAllByIdForUpdate(ids);
        if (parts.size() != ids.stream().distinct().count()) {
            Long missing = ids.stream()
                    .filter(id -> parts.stream().noneMatch(part -> part.getId().equals(id)))
                    .findFirst()
                    .orElseThrow();
            throw new ResourceNotFoundException(RESOURCE, missing);
        }
        return parts;
    }

    private Part getPart(Long id) {
        return partRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private static void normalize(Part part, String code) {
        part.setCode(code);
        part.setName(part.getName().trim());
        // @Digits guarantees at most two decimals, so this only pads (e.g. 49.9 -> 49.90)
        part.setUnitPrice(part.getUnitPrice().setScale(2, RoundingMode.UNNECESSARY));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static BusinessRuleException codeTaken(String code) {
        return new BusinessRuleException(ErrorCode.PART_CODE_ALREADY_EXISTS,
                "A part with code '%s' already exists.".formatted(code));
    }
}
