package ru.otus.financetracker.api.transactions;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.financetracker.application.transactions.CreateTransactionCommand;
import ru.otus.financetracker.application.transactions.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    ResponseEntity<TransactionResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTransactionRequest request) {
        var transaction = transactionService.create(userId(jwt), new CreateTransactionCommand(
                request.categoryId(), request.amount(), request.currency(), request.exchangeRateToBase(), request.transactionDate(),
                request.description(), request.transactionType()
        ));
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.id()))
                .body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{transactionId}")
    TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId) {
        return TransactionResponse.from(transactionService.get(userId(jwt), transactionId));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
