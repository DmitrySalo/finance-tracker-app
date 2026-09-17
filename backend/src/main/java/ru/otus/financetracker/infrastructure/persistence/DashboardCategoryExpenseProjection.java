package ru.otus.financetracker.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

interface DashboardCategoryExpenseProjection {

    UUID getCategoryId();

    String getCategoryName();

    String getCategoryIcon();

    String getCategoryColor();

    BigDecimal getAmount();
}
