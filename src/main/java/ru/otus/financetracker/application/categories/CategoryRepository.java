package ru.otus.financetracker.application.categories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.otus.financetracker.domain.categories.Category;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findByIdAndUserId(UUID categoryId, UUID userId);

    Page<Category> findAllByUserId(UUID userId, Pageable pageable);

    void delete(Category category);
}
