package ru.otus.financetracker.application.categories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ru.otus.financetracker.domain.categories.Category;

public interface CategoryRepository {

    void save(Category category);

    Optional<Category> findByIdAndUserId(UUID categoryId, UUID userId);

    List<Category> findAllByUserId(UUID userId);
}
