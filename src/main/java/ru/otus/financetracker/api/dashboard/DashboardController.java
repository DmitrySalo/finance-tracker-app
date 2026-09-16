package ru.otus.financetracker.api.dashboard;

import java.time.YearMonth;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.application.dashboard.DashboardService;

@RestController
@Validated
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Expense analytics for the current user")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private static final String YEAR_MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Get monthly dashboard", description = "Returns expense breakdown and top categories for month in YYYY-MM format.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Dashboard"), @ApiResponse(responseCode = "400", description = "Invalid month"), @ApiResponse(responseCode = "401", description = "Authentication is required")})
    DashboardResponse getDashboard(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam @NotBlank @Pattern(regexp = YEAR_MONTH_PATTERN) String month) {
        return DashboardResponse.from(dashboardService.getDashboard(userId(jwt), YearMonth.parse(month)));
    }

    @GetMapping("/spending-trend")
    @Operation(summary = "Get six-month spending trend", description = "Returns monthly expenses ending at endMonth in YYYY-MM format.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Spending trend"), @ApiResponse(responseCode = "400", description = "Invalid end month"), @ApiResponse(responseCode = "401", description = "Authentication is required")})
    SpendingTrendResponse getSpendingTrend(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam @NotBlank @Pattern(regexp = YEAR_MONTH_PATTERN) String endMonth) {
        return SpendingTrendResponse.from(dashboardService.getSpendingTrend(userId(jwt), YearMonth.parse(endMonth)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
