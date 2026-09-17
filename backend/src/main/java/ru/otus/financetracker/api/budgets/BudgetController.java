package ru.otus.financetracker.api.budgets;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import ru.otus.financetracker.application.budgets.BudgetService;
import ru.otus.financetracker.application.budgets.CreateBudgetCommand;
import ru.otus.financetracker.application.budgets.UpdateBudgetCommand;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Current user's monthly expense budgets")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @Operation(summary = "Create a budget")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Budget created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "409", description = "Budget already exists")
    })
    ResponseEntity<BudgetResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBudgetRequest request
    ) {
        var budget = budgetService.create(
                userId(jwt),
                new CreateBudgetCommand(
                        request.categoryId(),
                        request.budgetMonth(),
                        request.limitAmount(),
                        request.currency()
                )
        );
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + budget.budget().id()))
                .body(BudgetResponse.from(budget));
    }

    @GetMapping("/{budgetId}")
    @Operation(summary = "Get a budget")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Budget"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Budget not found")
    })
    BudgetResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID budgetId) {
        return BudgetResponse.from(budgetService.get(userId(jwt), budgetId));
    }

    @GetMapping
    @Operation(
            summary = "List budgets",
            description = "Lists budgets ordered by month. Page size is limited to 100."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Budget page"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    PageResponse<BudgetResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var budgets = budgetService.list(
                userId(jwt),
                PageRequest.of(
                        page,
                        size,
                        Sort.by("budgetMonth").ascending().and(Sort.by("id").ascending())
                )
        );
        return new PageResponse<>(
                budgets.getContent().stream().map(BudgetResponse::from).toList(),
                new PageResponse.Page(
                        budgets.getNumber(),
                        budgets.getSize(),
                        budgets.getTotalElements(),
                        budgets.getTotalPages()
                )
        );
    }

    @PatchMapping("/{budgetId}")
    @Operation(summary = "Update a budget")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Budget updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Budget not found"),
            @ApiResponse(responseCode = "409", description = "Version conflict")
    })
    BudgetResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID budgetId,
            @Valid @RequestBody UpdateBudgetRequest request
    ) {
        return BudgetResponse.from(
                budgetService.update(
                        userId(jwt),
                        budgetId,
                        new UpdateBudgetCommand(
                                request.version(),
                                request.categoryId(),
                                request.budgetMonth(),
                                request.limitAmount(),
                                request.currency()
                        )
                )
        );
    }

    @DeleteMapping("/{budgetId}")
    @Operation(summary = "Delete a budget")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Budget deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid version"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Budget not found"),
            @ApiResponse(responseCode = "409", description = "Version conflict")
    })
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID budgetId,
            @RequestParam @Min(0) long version
    ) {
        budgetService.delete(userId(jwt), budgetId, version);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
