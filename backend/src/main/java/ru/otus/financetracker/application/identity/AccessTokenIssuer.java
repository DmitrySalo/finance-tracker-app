package ru.otus.financetracker.application.identity;

import ru.otus.financetracker.domain.identity.User;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user);
}
