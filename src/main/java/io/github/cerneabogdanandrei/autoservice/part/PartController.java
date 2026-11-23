package io.github.cerneabogdanandrei.autoservice.part;

import io.github.cerneabogdanandrei.autoservice.part.dto.PartRequest;
import io.github.cerneabogdanandrei.autoservice.part.dto.PartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/parts")
@Tag(name = "Parts", description = "Parts inventory")
public class PartController {

    private final PartService partService;

    PartController(PartService partService) {
        this.partService = partService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List parts, optionally only those with stock below a threshold")
    public PagedModel<PartResponse> findAll(
            @Parameter(description = "Only parts whose stock is strictly below this value")
            @RequestParam(required = false) @PositiveOrZero Integer stockBelow,
            @ParameterObject @SortDefault("code") Pageable pageable) {
        return new PagedModel<>(partService.findAll(stockBelow, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a part")
    public PartResponse findById(@PathVariable Long id) {
        return partService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Add a part to the catalogue")
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartRequest request) {
        PartResponse created = partService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Update a part (including stock adjustments)")
    public PartResponse update(@PathVariable Long id, @Valid @RequestBody PartRequest request) {
        return partService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Delete a part that has never been used by an intervention")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        partService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
