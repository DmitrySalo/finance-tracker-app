package ru.otus.financetracker.application.categories;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.financetracker.domain.categories.Category;
import ru.otus.financetracker.shared.web.ResourceNotFoundException;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    public Category create(UUID userId, CreateCategoryCommand command) {
        Instant now = clock.instant();
        return categoryRepository.save(new Category(
                UUID.randomUUID(),
                userId,
                command.name().strip(),
                command.transactionType(),
                command.icon().strip(),
                command.color(),
                now,
                now,
                0
        ));
    }

    @Transactional(readOnly = true)
    public Page<Category> list(UUID userId, Pageable pageable) {
        return categoryRepository.findAllByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Category get(UUID userId, UUID categoryId) {
        return findOwnedCategory(userId, categoryId);
    }

    @Transactional
    public Category update(UUID userId, UUID categoryId, UpdateCategoryCommand command) {
        var category = findOwnedCategory(userId, categoryId);
        if (category.version() != command.version()) {
            throw new OptimisticLockingFailureException("Category has been modified.");
        }
        return categoryRepository.save(new Category(
                category.id(),
                category.userId(),
                command.name() == null ? category.name() : command.name().strip(),
                command.transactionType() == null ? category.transactionType() : command.transactionType(),
                command.icon() == null ? category.icon() : command.icon().strip(),
                command.color() == null ? category.color() : command.color(),
                category.createdAt(),
                clock.instant(),
                category.version()
        ));
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        categoryRepository.delete(findOwnedCategory(userId, categoryId));
    }

    private Category findOwnedCategory(UUID userId, UUID categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }
}
