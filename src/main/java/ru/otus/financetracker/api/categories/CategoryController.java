package ru.otus.financetracker.api.categories;

import java.net.URI;
import java.util.List;
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
import ru.otus.financetracker.application.categories.CategoryService;
import ru.otus.financetracker.application.categories.CreateCategoryCommand;
import ru.otus.financetracker.application.categories.UpdateCategoryCommand;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCategoryRequest request) {
        var category = categoryService.create(userId(jwt), new CreateCategoryCommand(
                request.name(), request.transactionType(), request.icon(), request.color()
        ));
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id()))
                .body(CategoryResponse.from(category));
    }

    @GetMapping
    PageResponse<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var categories = categoryService.list(userId(jwt), PageRequest.of(page, size, Sort.by("name").ascending()
                .and(Sort.by("id").ascending())));
        return new PageResponse<>(categories.getContent().stream().map(CategoryResponse::from).toList(),
                new PageResponse.Page(categories.getNumber(), categories.getSize(), categories.getTotalElements(),
                        categories.getTotalPages()));
    }

    @GetMapping("/{categoryId}")
    CategoryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        return CategoryResponse.from(categoryService.get(userId(jwt), categoryId));
    }

    @PatchMapping("/{categoryId}")
    CategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId,
                            @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(categoryService.update(userId(jwt), categoryId, new UpdateCategoryCommand(
                request.version(), request.name(), request.transactionType(), request.icon(), request.color()
        )));
    }

    @DeleteMapping("/{categoryId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        categoryService.delete(userId(jwt), categoryId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
