package ru.otus.financetracker.api.dashboard;

import java.time.YearMonth;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class DashboardController {

    private static final String YEAR_MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    DashboardResponse getDashboard(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam @NotBlank @Pattern(regexp = YEAR_MONTH_PATTERN) String month) {
        return DashboardResponse.from(dashboardService.getDashboard(userId(jwt), YearMonth.parse(month)));
    }

    @GetMapping("/spending-trend")
    SpendingTrendResponse getSpendingTrend(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam @NotBlank @Pattern(regexp = YEAR_MONTH_PATTERN) String endMonth) {
        return SpendingTrendResponse.from(dashboardService.getSpendingTrend(userId(jwt), YearMonth.parse(endMonth)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
