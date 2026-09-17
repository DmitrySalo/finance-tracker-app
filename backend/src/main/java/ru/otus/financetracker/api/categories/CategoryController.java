package ru.otus.financetracker.api.categories;

import java.net.URI;
import java.util.List;
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

import ru.otus.financetracker.application.categories.CategoryService;
import ru.otus.financetracker.application.categories.CreateCategoryCommand;
import ru.otus.financetracker.application.categories.UpdateCategoryCommand;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Current user's income and expense categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @Operation(summary = "Create a category")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "409", description = "Category already exists")
    })
    ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        var category = categoryService.create(
                userId(jwt),
                new CreateCategoryCommand(
                        request.name(),
                        request.transactionType(),
                        request.icon(),
                        request.color()
                )
        );
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id()))
                .body(CategoryResponse.from(category));
    }

    @GetMapping
    @Operation(
            summary = "List categories",
            description = "Lists categories ordered by name. Page size is limited to 100."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category page"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
    PageResponse<CategoryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        var categories = categoryService.list(
                userId(jwt),
                PageRequest.of(
                        page,
                        size,
                        Sort.by("name").ascending().and(Sort.by("id").ascending())
                )
        );
        return new PageResponse<>(
                categories.getContent().stream().map(CategoryResponse::from).toList(),
                new PageResponse.Page(
                        categories.getNumber(),
                        categories.getSize(),
                        categories.getTotalElements(),
                        categories.getTotalPages()
                )
        );
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get a category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    CategoryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        return CategoryResponse.from(
                categoryService.get(userId(jwt), categoryId)
        );
    }

    @PatchMapping("/{categoryId}")
    @Operation(summary = "Update a category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "Version conflict")
    })
    CategoryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return CategoryResponse.from(
                categoryService.update(
                        userId(jwt),
                        categoryId,
                        new UpdateCategoryCommand(
                                request.version(),
                                request.name(),
                                request.transactionType(),
                                request.icon(),
                                request.color()
                        )
                )
        );
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Delete a category")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "Category is in use")
    })
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        categoryService.delete(userId(jwt), categoryId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
