package ru.otus.financetracker.application.identity;

import ru.otus.financetracker.domain.identity.User;

public interface UserRegistrationRepository {

    void save(User user);
}
