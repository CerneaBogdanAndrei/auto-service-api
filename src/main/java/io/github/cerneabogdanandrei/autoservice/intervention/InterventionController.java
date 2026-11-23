package io.github.cerneabogdanandrei.autoservice.intervention;

import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionCreateRequest;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionResponse;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionSummaryResponse;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.InterventionUpdateRequest;
import io.github.cerneabogdanandrei.autoservice.intervention.dto.PartUsageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * Employees may call the read, update and part endpoints, but only for interventions assigned
 * to them (enforced in the service layer); creating and deleting is for ADMIN and OWNER.
 */
@RestController
@RequestMapping("/api/interventions")
@Tag(name = "Interventions", description = "Work performed during appointments and the parts it uses")
public class InterventionController {

    private final InterventionService interventionService;

    InterventionController(InterventionService interventionService) {
        this.interventionService = interventionService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List interventions (employees only see their own)")
    public PagedModel<InterventionSummaryResponse> findAll(
            @RequestParam(required = false) Long appointmentId,
            @Parameter(description = "Ignored for employees, who always get their own interventions")
            @RequestParam(required = false) Long assigneeId,
            @ParameterObject @SortDefault("createdAt") Pageable pageable,
            AuthenticatedUser user) {
        return new PagedModel<>(interventionService.findAll(appointmentId, assigneeId, user, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get an intervention with its parts and totals")
    public InterventionResponse findById(@PathVariable Long id, AuthenticatedUser user) {
        return interventionService.findById(id, user);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Create an intervention on an IN_PROGRESS appointment, optionally using parts")
    public ResponseEntity<InterventionResponse> create(@Valid @RequestBody InterventionCreateRequest request) {
        InterventionResponse created = interventionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update description and labour cost (reassigning is for ADMIN and OWNER)")
    public InterventionResponse update(@PathVariable Long id, @Valid @RequestBody InterventionUpdateRequest request,
                                       AuthenticatedUser user) {
        return interventionService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Delete an intervention and return its parts to stock")
    public ResponseEntity<Void> delete(@PathVariable Long id, AuthenticatedUser user) {
        interventionService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/parts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Use a part: takes it from stock (409 INSUFFICIENT_STOCK if not enough)")
    public InterventionResponse addPart(@PathVariable Long id, @Valid @RequestBody PartUsageRequest request,
                                        AuthenticatedUser user) {
        return interventionService.addPart(id, request, user);
    }

    @DeleteMapping("/{id}/parts/{partId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a part from the intervention and return it to stock")
    public InterventionResponse removePart(@PathVariable Long id, @PathVariable Long partId, AuthenticatedUser user) {
        return interventionService.removePart(id, partId, user);
    }
}
