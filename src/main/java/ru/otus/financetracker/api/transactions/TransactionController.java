package ru.otus.financetracker.api.transactions;

import java.net.URI;
import java.math.BigDecimal;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.otus.financetracker.application.transactions.CreateTransactionCommand;
import ru.otus.financetracker.application.transactions.InvalidTransactionFilterException;
import ru.otus.financetracker.application.transactions.TransactionFilter;
import ru.otus.financetracker.application.transactions.TransactionExportCursor;
import ru.otus.financetracker.application.transactions.TransactionService;
import ru.otus.financetracker.configuration.ApplicationProperties;
import ru.otus.financetracker.application.transactions.UpdateTransactionCommand;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.infrastructure.csv.TransactionCsvWriter;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final int EXPORT_CHUNK_SIZE = 100;

    private final TransactionService transactionService;
    private final TransactionCsvWriter transactionCsvWriter;
    private final ApplicationProperties applicationProperties;

    public TransactionController(TransactionService transactionService, TransactionCsvWriter transactionCsvWriter,
                                 ApplicationProperties applicationProperties) {
        this.transactionService = transactionService;
        this.transactionCsvWriter = transactionCsvWriter;
        this.applicationProperties = applicationProperties;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    ResponseEntity<StreamingResponseBody> export(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) TransactionType transactionType
    ) {
        UUID userId = userId(jwt);
        var filter = new TransactionFilter(fromDate, toDate, categoryId, minAmount, maxAmount, transactionType);
        transactionService.validateExport(userId, filter);
        StreamingResponseBody body = outputStream -> {
            var writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            transactionCsvWriter.writeHeader(writer);
            int exportedRows = 0;
            TransactionExportCursor cursor = null;
            while (exportedRows < applicationProperties.limits().maxCsvRows()) {
                int remainingRows = applicationProperties.limits().maxCsvRows() - exportedRows;
                var transactions = transactionService.exportChunk(userId, filter, cursor,
                        Math.min(remainingRows, EXPORT_CHUNK_SIZE));
                if (transactions.isEmpty()) {
                    break;
                }
                transactionCsvWriter.writeTransactions(writer, transactions);
                exportedRows += transactions.size();
                var lastTransaction = transactions.getLast();
                cursor = new TransactionExportCursor(lastTransaction.transactionDate(), lastTransaction.id());
            }
            writer.flush();
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
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

    @PatchMapping("/{transactionId}")
    TransactionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId,
                               @Valid @RequestBody UpdateTransactionRequest request) {
        return TransactionResponse.from(transactionService.update(userId(jwt), transactionId, new UpdateTransactionCommand(
                request.version(), request.categoryId(), request.amount(), request.currency(), request.exchangeRateToBase(),
                request.transactionDate(), request.description(), request.transactionType()
        )));
    }

    @DeleteMapping("/{transactionId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId,
                                @RequestParam @Min(0) long version) {
        transactionService.delete(userId(jwt), transactionId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "transactionDate,desc") String sort
    ) {
        var transactions = transactionService.list(userId(jwt),
                new TransactionFilter(fromDate, toDate, categoryId, minAmount, maxAmount, transactionType),
                PageRequest.of(page, size, toSort(sort)));
        return new PageResponse<>(transactions.getContent().stream().map(TransactionResponse::from).toList(),
                new PageResponse.Page(transactions.getNumber(), transactions.getSize(), transactions.getTotalElements(),
                        transactions.getTotalPages()));
    }

    private Sort toSort(String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2) {
            throw new InvalidTransactionFilterException();
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransactionFilterException();
        }
        if (!parts[0].equals("transactionDate") && !parts[0].equals("amount")) {
            throw new InvalidTransactionFilterException();
        }
        return Sort.by(direction, parts[0]).and(Sort.by(direction, "id"));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
