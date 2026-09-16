package ru.otus.financetracker.shared;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        Page page
) {

    public record Page(
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
