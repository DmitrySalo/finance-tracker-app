package ru.otus.financetracker.api.recurring;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.application.recurring.CreateRecurringTransactionCommand;
import ru.otus.financetracker.application.recurring.RecurringTransactionService;
import ru.otus.financetracker.application.recurring.UpdateRecurringTransactionCommand;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/recurring-transactions")
@Tag(name = "Recurring transactions", description = "Current user's monthly recurring transaction rules")
@SecurityRequirement(name = "bearerAuth")
public class RecurringTransactionController {
    private final RecurringTransactionService recurringTransactionService;
    public RecurringTransactionController(RecurringTransactionService recurringTransactionService) { this.recurringTransactionService = recurringTransactionService; }
    @PostMapping
    @Operation(summary = "Create a recurring transaction rule")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Rule created"), @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "401", description = "Authentication is required")})
    ResponseEntity<RecurringTransactionResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRecurringTransactionRequest request) {
        var rule = recurringTransactionService.create(userId(jwt), new CreateRecurringTransactionCommand(request.categoryId(), request.amount(), request.currency(), request.exchangeRateToBase(), request.description(), request.transactionType(), request.dayOfMonth(), request.startDate(), request.active()));
        return ResponseEntity.created(URI.create("/api/v1/recurring-transactions/" + rule.id())).body(RecurringTransactionResponse.from(rule));
    }
    @GetMapping("/{id}")
    @Operation(summary = "Get a recurring transaction rule")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Rule"), @ApiResponse(responseCode = "401", description = "Authentication is required"), @ApiResponse(responseCode = "404", description = "Rule not found")})
    RecurringTransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return RecurringTransactionResponse.from(recurringTransactionService.get(userId(jwt), id)); }
    @GetMapping
    @Operation(summary = "List recurring transaction rules", description = "Lists rules ordered by next occurrence. Page size is limited to 100.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Rule page"), @ApiResponse(responseCode = "400", description = "Invalid pagination"), @ApiResponse(responseCode = "401", description = "Authentication is required")})
    PageResponse<RecurringTransactionResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var rules = recurringTransactionService.list(userId(jwt), PageRequest.of(page, size, Sort.by("nextOccurrenceDate").ascending().and(Sort.by("id").ascending())));
        return new PageResponse<>(rules.getContent().stream().map(RecurringTransactionResponse::from).toList(), new PageResponse.Page(rules.getNumber(), rules.getSize(), rules.getTotalElements(), rules.getTotalPages()));
    }
    @PatchMapping("/{id}")
    @Operation(summary = "Update a recurring transaction rule")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Rule updated"), @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "401", description = "Authentication is required"), @ApiResponse(responseCode = "404", description = "Rule not found"), @ApiResponse(responseCode = "409", description = "Version conflict")})
    RecurringTransactionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateRecurringTransactionRequest request) {
        return RecurringTransactionResponse.from(recurringTransactionService.update(userId(jwt), id, new UpdateRecurringTransactionCommand(request.version(), request.categoryId(), request.amount(), request.currency(), request.exchangeRateToBase(), request.description(), request.transactionType(), request.dayOfMonth(), request.startDate(), request.active())));
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a recurring transaction rule")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Rule deleted"), @ApiResponse(responseCode = "400", description = "Invalid version"), @ApiResponse(responseCode = "401", description = "Authentication is required"), @ApiResponse(responseCode = "404", description = "Rule not found"), @ApiResponse(responseCode = "409", description = "Version conflict")})
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam @Min(0) long version) { recurringTransactionService.delete(userId(jwt), id, version); return ResponseEntity.noContent().build(); }
    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
