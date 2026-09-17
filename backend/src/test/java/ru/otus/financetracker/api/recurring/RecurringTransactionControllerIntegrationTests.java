package ru.otus.financetracker.api.recurring;

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
class RecurringTransactionControllerIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Владелец создает, читает, изменяет и удаляет повторяющееся правило")
    void shouldCreateAndListOwnedRecurringRule() throws Exception {
        String token = registerAndLogin("recurring-owner@example.test");
        String categoryId = createCategory(token);
        String body = request(categoryId);
        var result = mockMvc.perform(post("/api/v1/recurring-transactions")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dayOfMonth").value(31))
            .andExpect(jsonPath("$.nextOccurrenceDate").value("2026-02-28"))
            .andReturn();
        String id = new tools.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asText();
        mockMvc.perform(
            get("/api/v1/recurring-transactions/{id}", id)
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(
            get("/api/v1/recurring-transactions")
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(id));
        mockMvc.perform(
            patch("/api/v1/recurring-transactions/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"version\":0,\"active\":false}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(
            delete("/api/v1/recurring-transactions/{id}?version=1", id)
                .header("Authorization", "Bearer " + token)
        )
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Повторяющиеся операции требуют аутентификацию и не принимают чужую категорию")
    void shouldRequireAuthenticationAndRejectForeignCategory() throws Exception {
        mockMvc.perform(get("/api/v1/recurring-transactions"))
            .andExpect(status().isUnauthorized());
        String owner = registerAndLogin("recurring-owner2@example.test");
        String other = registerAndLogin("recurring-other@example.test");
        mockMvc.perform(post("/api/v1/recurring-transactions")
                .header("Authorization", "Bearer " + other)
                .contentType("application/json")
                .content(request(createCategory(owner))))
            .andExpect(status().isNotFound());
    }

    private String request(String categoryId) {
        return "{\"categoryId\":\"%s\",\"amount\":10.0000,\"currency\":\"USD\",\"exchangeRateToBase\":1.00000000,\"description\":\"Rent\",\"transactionType\":\"EXPENSE\",\"dayOfMonth\":31,\"startDate\":\"2026-02-01\",\"active\":true}".formatted(categoryId);
    }
    private String createCategory(String token) throws Exception {
        var result = mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Rent %s\",\"transactionType\":\"EXPENSE\",\"icon\":\"home\",\"color\":\"#0A1B2C\"}".formatted(java.util.UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn();
        return new tools.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asText();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"a-secure-password\",\"displayName\":\"Test User\",\"baseCurrency\":\"USD\"}".formatted(email)))
            .andExpect(status().isCreated());
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"a-secure-password\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
        return new tools.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    }
}
