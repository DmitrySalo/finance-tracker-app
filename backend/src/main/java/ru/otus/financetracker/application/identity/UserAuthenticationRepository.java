package ru.otus.financetracker.application.identity;

import java.util.Optional;
import java.util.UUID;

import ru.otus.financetracker.domain.identity.User;

public interface UserAuthenticationRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);
}
