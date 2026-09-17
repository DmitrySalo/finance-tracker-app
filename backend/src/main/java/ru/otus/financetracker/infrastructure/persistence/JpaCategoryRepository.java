package ru.otus.financetracker.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import ru.otus.financetracker.application.categories.CategoryRepository;
import ru.otus.financetracker.domain.categories.Category;

@Repository
public class JpaCategoryRepository implements CategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;

    public JpaCategoryRepository(CategoryJpaRepository categoryJpaRepository) {
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public Category save(Category category) {
        categoryJpaRepository.saveAndFlush(toEntity(category));
        return categoryJpaRepository.findByIdAndUserId(category.id(), category.userId())
                .map(this::toDomain)
                .orElseThrow();
    }

    @Override
    public Optional<Category> findByIdAndUserId(UUID categoryId, UUID userId) {
        return categoryJpaRepository.findByIdAndUserId(categoryId, userId).map(this::toDomain);
    }

    @Override
    public Page<Category> findAllByUserId(UUID userId, Pageable pageable) {
        return categoryJpaRepository.findAllByUserId(userId, pageable).map(this::toDomain);
    }

    @Override
    public void delete(Category category) {
        categoryJpaRepository.delete(toEntity(category));
        categoryJpaRepository.flush();
    }

    private CategoryJpaEntity toEntity(Category category) {
        return new CategoryJpaEntity(
                category.id(),
                category.userId(),
                category.name(),
                category.transactionType(),
                category.icon(),
                category.color(),
                category.createdAt(),
                category.updatedAt(),
                category.version()
        );
    }

    private Category toDomain(CategoryJpaEntity entity) {
        return new Category(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getTransactionType(),
                entity.getIcon(),
                entity.getColor(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
