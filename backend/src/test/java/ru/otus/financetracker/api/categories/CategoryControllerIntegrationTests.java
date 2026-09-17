package ru.otus.financetracker.api.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class CategoryControllerIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Владелец создает, читает, изменяет и удаляет свою категорию")
    void shouldCreateListGetUpdateAndDeleteOwnedCategory() throws Exception {
        String token = registerAndLogin("owner@example.test");

        var createResponse = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":" Food ","transactionType":"EXPENSE","icon":"utensils","color":"#0A1B2C"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/v1/categories/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$.icon").value("utensils"))
                .andExpect(jsonPath("$.color").value("#0A1B2C"))
                .andReturn();
        String categoryId = new tools.jackson.databind.ObjectMapper().readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();
        long version = new tools.jackson.databind.ObjectMapper().readTree(createResponse.getResponse().getContentAsString())
                .get("version").asLong();

        mockMvc.perform(get("/api/v1/categories")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(categoryId))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20));
        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"));
        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"version":%d,"name":"Groceries","icon":"cart"}
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Groceries"))
                .andExpect(jsonPath("$.icon").value("cart"));
        mockMvc.perform(delete("/api/v1/categories/{categoryId}", categoryId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM categories", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Запросы категорий отклоняют неаутентифицированного пользователя и неверные данные")
    void shouldRejectInvalidCategoryAndUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType("application/json")
                        .content("""
                                {"version":0,"name":"Food","transactionType":"EXPENSE","icon":"utensils","color":"#0A1B2C"}
                                """))
                .andExpect(status().isUnauthorized());

        String token = registerAndLogin("owner@example.test");
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":" ","transactionType":"EXPENSE","icon":"","color":"blue"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.length()").value(3));
    }

    @Test
    @DisplayName("Категория другого пользователя скрыта при чтении и изменении")
    void shouldHideCategoryOwnedByAnotherUser() throws Exception {
        String ownerToken = registerAndLogin("owner@example.test");
        String otherToken = registerAndLogin("other@example.test");
        var createResponse = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"name":"Food","transactionType":"EXPENSE","icon":"utensils","color":"#0A1B2C"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = new tools.jackson.databind.ObjectMapper().readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"name":"Food","transactionType":"EXPENSE","icon":"utensils","color":"#0A1B2C"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/categories/not-a-uuid")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(delete("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Изменение категории с устаревшей версией возвращает конфликт")
    void shouldRejectUpdateWithStaleVersion() throws Exception {
        String token = registerAndLogin("owner@example.test");
        var createResponse = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Food","transactionType":"EXPENSE","icon":"utensils","color":"#0A1B2C"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = new tools.jackson.databind.ObjectMapper().readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"name":"Groceries"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"version":0,"name":"Food and drinks"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Изменение категории с отрицательной версией отклоняется")
    void shouldRejectNegativeCategoryVersion() throws Exception {
        String token = registerAndLogin("owner@example.test");
        var createResponse = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Food","transactionType":"EXPENSE","icon":"utensils","color":"#0A1B2C"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = new tools.jackson.databind.ObjectMapper().readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"version":-1,"name":"Groceries"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"a-secure-password","displayName":"Test User","baseCurrency":"USD"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"a-secure-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new tools.jackson.databind.ObjectMapper().readTree(loginResponse.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
