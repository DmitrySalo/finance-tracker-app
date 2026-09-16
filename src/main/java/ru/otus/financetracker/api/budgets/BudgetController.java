package ru.otus.financetracker.api.budgets;

import java.net.URI;
import java.util.UUID;

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
public class BudgetController {
    private final BudgetService budgetService;
    public BudgetController(BudgetService budgetService) { this.budgetService = budgetService; }

    @PostMapping
    ResponseEntity<BudgetResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBudgetRequest request) {
        var budget = budgetService.create(userId(jwt), new CreateBudgetCommand(request.categoryId(), request.budgetMonth(),
                request.limitAmount(), request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + budget.budget().id())).body(BudgetResponse.from(budget));
    }
    @GetMapping("/{budgetId}")
    BudgetResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID budgetId) {
        return BudgetResponse.from(budgetService.get(userId(jwt), budgetId));
    }
    @GetMapping
    PageResponse<BudgetResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var budgets = budgetService.list(userId(jwt), PageRequest.of(page, size, Sort.by("budgetMonth").ascending()
                .and(Sort.by("id").ascending())));
        return new PageResponse<>(budgets.getContent().stream().map(BudgetResponse::from).toList(),
                new PageResponse.Page(budgets.getNumber(), budgets.getSize(), budgets.getTotalElements(), budgets.getTotalPages()));
    }
    @PatchMapping("/{budgetId}")
    BudgetResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID budgetId,
                          @Valid @RequestBody UpdateBudgetRequest request) {
        return BudgetResponse.from(budgetService.update(userId(jwt), budgetId, new UpdateBudgetCommand(request.version(),
                request.categoryId(), request.budgetMonth(), request.limitAmount(), request.currency())));
    }
    @DeleteMapping("/{budgetId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID budgetId, @RequestParam @Min(0) long version) {
        budgetService.delete(userId(jwt), budgetId, version);
        return ResponseEntity.noContent().build();
    }
    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
