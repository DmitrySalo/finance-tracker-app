package ru.otus.financetracker.infrastructure.persistence;

import java.sql.SQLException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import ru.otus.financetracker.application.identity.DuplicateEmailException;
import ru.otus.financetracker.application.identity.UserRegistrationRepository;
import ru.otus.financetracker.domain.identity.User;

@Repository
public class JpaUserRegistrationRepository implements UserRegistrationRepository {

    private final UserJpaRepository userJpaRepository;
    private final TransactionTemplate transactionTemplate;

    public JpaUserRegistrationRepository(
            UserJpaRepository userJpaRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.userJpaRepository = userJpaRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void save(User user) {
        try {
            transactionTemplate.executeWithoutResult(
                    status -> userJpaRepository.saveAndFlush(toEntity(user))
            );
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new DuplicateEmailException(exception);
            }
            throw exception;
        }
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage().contains("uq_users_email")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.displayName(),
                user.baseCurrency(),
                user.createdAt(),
                user.updatedAt(),
                user.version()
        );
    }
}
