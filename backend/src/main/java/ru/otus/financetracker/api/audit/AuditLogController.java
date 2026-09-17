package ru.otus.financetracker.api.audit;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.application.audit.AuditLogService;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit", description = "Current user's transaction and budget change history")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) { this.auditLogService = auditLogService; }

    @GetMapping
    @Operation(summary = "List audit log entries", description = "Lists the current user's transaction or budget audit records. Page size is limited to 100.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Audit log page"), @ApiResponse(responseCode = "400", description = "Invalid filter or pagination"), @ApiResponse(responseCode = "401", description = "Authentication is required")})
    PageResponse<AuditLogResponse> list(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam @NotNull AuditEntityType entityType,
                                        @RequestParam(required = false) UUID entityId,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var entries = auditLogService.list(UUID.fromString(jwt.getSubject()), entityType.name(), entityId,
                PageRequest.of(page, size, Sort.by("occurredAt").descending().and(Sort.by("id").descending())));
        return new PageResponse<>(entries.getContent().stream().map(AuditLogResponse::from).toList(),
                new PageResponse.Page(entries.getNumber(), entries.getSize(), entries.getTotalElements(), entries.getTotalPages()));
    }

    enum AuditEntityType { TRANSACTION, BUDGET }
}
