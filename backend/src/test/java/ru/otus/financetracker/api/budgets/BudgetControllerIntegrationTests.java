package ru.otus.financetracker.api.budgets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.otus.financetracker.support.PostgresIntegrationTestSupport;

@AutoConfigureMockMvc
class BudgetControllerIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Владелец создает, читает, изменяет и удаляет бюджет")
    void shouldCreateListGetUpdateAndDeleteBudget() throws Exception {
        String token = registerAndLogin("budget-owner@example.test");
        String categoryId = createCategory(token, "EXPENSE");
        String budgetId = createBudget(token, categoryId, "2026-09-01", "500.0000");
        mockMvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(budgetId))
            .andExpect(jsonPath("$.items[0].spentAmount").value("0.0000"))
            .andExpect(jsonPath("$.items[0].remainingAmount").value("500.0000"))
            .andExpect(jsonPath("$.items[0].percentage").value("0.00"));
        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgetMonth").value("2026-09-01"));
        mockMvc.perform(patch("/api/v1/budgets/{id}", budgetId)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"version\":0,\"limitAmount\":600.0000,\"currency\":\"USD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limitAmount").value("600.0000"))
            .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(delete("/api/v1/budgets/{id}?version=1", budgetId).header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Бюджет отклоняет неверные месяц, лимит, категорию и дубликат")
    void shouldRejectInvalidMonthLimitIncomeAndDuplicateBudget() throws Exception {
        String token = registerAndLogin("budget-validation@example.test");
        String expense = createCategory(token, "EXPENSE");
        String income = createCategory(token, "INCOME");
        mockMvc.perform(post("/api/v1/budgets").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(request(expense, "2026-09-02", "500.0000")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/budgets").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(request(expense, "2026-09-01", "0")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/budgets").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(request(income, "2026-09-01", "500.0000")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/budgets").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"categoryId\":\"%s\",\"budgetMonth\":\"2026-10-01\",\"limitAmount\":500.0000,\"currency\":\"EUR\"}".formatted(expense)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        createBudget(token, expense, "2026-09-01", "500.0000");
        mockMvc.perform(post("/api/v1/budgets").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(request(expense, "2026-09-01", "600.0000")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Бюджеты требуют аутентификацию")
    void shouldRequireAuthenticationForBudgets() throws Exception {
        mockMvc.perform(get("/api/v1/budgets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Чужой бюджет скрыт, а устаревшая версия вызывает конфликт")
    void shouldHideForeignBudgetAndRejectStaleUpdateAndDelete() throws Exception {
        String owner = registerAndLogin("budget-owner2@example.test");
        String other = registerAndLogin("budget-other@example.test");
        String budgetId = createBudget(owner, createCategory(owner, "EXPENSE"), "2026-09-01", "500.0000");
        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId).header("Authorization", "Bearer " + other))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/budgets/{id}", budgetId).header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0,\"limitAmount\":600.0000}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/budgets/{id}", budgetId).header("Authorization", "Bearer " + owner).contentType("application/json")
                        .content("{\"version\":0,\"limitAmount\":700.0000}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(delete("/api/v1/budgets/{id}?version=0", budgetId).header("Authorization", "Bearer " + owner))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(delete("/api/v1/budgets/{id}?version=1", budgetId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private String createBudget(String token, String categoryId, String month, String amount) throws Exception {
        var response = mockMvc.perform(post("/api/v1/budgets").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(request(categoryId, month, amount)))
            .andExpect(status().isCreated())
            .andReturn();
        return new tools.jackson.databind.ObjectMapper()
            .readTree(response.getResponse().getContentAsString())
            .get("id")
            .asText();
    }

    private String request(String categoryId, String month, String amount) {
        return "{\"categoryId\":\"%s\",\"budgetMonth\":\"%s\",\"limitAmount\":%s,\"currency\":\"USD\"}".formatted(categoryId, month, amount);
    }

    private String createCategory(String token, String type) throws Exception {
        var response = mockMvc.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"name\":\"Food %s\",\"transactionType\":\"%s\",\"icon\":\"utensils\",\"color\":\"#0A1B2C\"}".formatted(java.util.UUID.randomUUID(), type)))
            .andExpect(status().isCreated())
            .andReturn();
        return new tools.jackson.databind.ObjectMapper()
            .readTree(response.getResponse().getContentAsString())
            .get("id")
            .asText();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"email\":\"%s\",\"password\":\"a-secure-password\",\"displayName\":\"Test User\",\"baseCurrency\":\"USD\"}".formatted(email)))
            .andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"%s\",\"password\":\"a-secure-password\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
        return new tools.jackson.databind.ObjectMapper()
            .readTree(response.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    }
}
