package ru.otus.financetracker.api.transactions;

import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;

import ru.otus.financetracker.api.transactions.imports.ImportColumnMappingRequest;
import ru.otus.financetracker.api.transactions.imports.TransactionImportConfirmationResponse;
import ru.otus.financetracker.api.transactions.imports.TransactionImportPreviewResponse;
import ru.otus.financetracker.application.transactions.CreateTransactionCommand;
import ru.otus.financetracker.application.transactions.InvalidTransactionFilterException;
import ru.otus.financetracker.application.transactions.TransactionFilter;
import ru.otus.financetracker.application.transactions.TransactionExportCursor;
import ru.otus.financetracker.application.transactions.TransactionService;
import ru.otus.financetracker.application.transactions.UpdateTransactionCommand;
import ru.otus.financetracker.application.transactions.imports.TransactionImportPreviewService;
import ru.otus.financetracker.configuration.ApplicationProperties;
import ru.otus.financetracker.domain.categories.TransactionType;
import ru.otus.financetracker.infrastructure.csv.TransactionCsvWriter;
import ru.otus.financetracker.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(
        name = "Transactions",
        description = "Current user's financial transactions and CSV import/export"
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private static final int EXPORT_CHUNK_SIZE = 100;

    private final TransactionService transactionService;
    private final TransactionCsvWriter transactionCsvWriter;
    private final ApplicationProperties applicationProperties;
    private final TransactionImportPreviewService transactionImportPreviewService;

    public TransactionController(
            TransactionService transactionService,
            TransactionCsvWriter transactionCsvWriter,
            ApplicationProperties applicationProperties,
            TransactionImportPreviewService transactionImportPreviewService
    ) {
        this.transactionService = transactionService;
        this.transactionCsvWriter = transactionCsvWriter;
        this.applicationProperties = applicationProperties;
        this.transactionImportPreviewService = transactionImportPreviewService;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(
            summary = "Export transactions as CSV",
            description = "Exports filtered transactions up to the configured CSV row limit."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV attachment"),
            @ApiResponse(responseCode = "400", description = "Invalid filter"),
            @ApiResponse(responseCode = "401", description = "Authentication is required")
    })
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
                var transactions = transactionService.exportChunk(
                        userId,
                        filter,
                        cursor,
                        Math.min(remainingRows, EXPORT_CHUNK_SIZE)
                );
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
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping
    @Operation(summary = "Create a transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transaction created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        var transaction = transactionService.create(
                userId(jwt),
                new CreateTransactionCommand(
                        request.categoryId(),
                        request.amount(),
                        request.currency(),
                        request.exchangeRateToBase(),
                        request.transactionDate(),
                        request.description(),
                        request.transactionType()
                )
        );
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.id()))
                .body(TransactionResponse.from(transaction));
    }

    @PostMapping(value = "/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Preview a CSV transaction import",
            description = "Validates a CSV file and mapping without saving transactions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Import preview"),
            @ApiResponse(responseCode = "400", description = "Invalid CSV, mapping, or file"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "413", description = "File exceeds configured size limit")
    })
    TransactionImportPreviewResponse previewImport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("mapping") ImportColumnMappingRequest mapping
    ) {
        return TransactionImportPreviewResponse.from(
                transactionImportPreviewService.preview(userId(jwt), file, mapping.columns())
        );
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Confirm a CSV transaction import",
            description = "Revalidates and atomically saves all valid CSV rows."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transactions imported"),
            @ApiResponse(responseCode = "400", description = "Invalid CSV, mapping, or file"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "413", description = "File exceeds configured size limit")
    })
    ResponseEntity<TransactionImportConfirmationResponse> confirmImport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("mapping") ImportColumnMappingRequest mapping
    ) {
        int importedCount = transactionImportPreviewService.confirm(userId(jwt), file, mapping.columns());
        return ResponseEntity.status(201).body(new TransactionImportConfirmationResponse(importedCount));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId) {
        return TransactionResponse.from(
                transactionService.get(userId(jwt), transactionId)
        );
    }

    @PatchMapping("/{transactionId}")
    @Operation(summary = "Update a transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Transaction not found"),
            @ApiResponse(responseCode = "409", description = "Version conflict")
    })
    TransactionResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateTransactionRequest request
    ) {
        return TransactionResponse.from(
                transactionService.update(
                        userId(jwt),
                        transactionId,
                        new UpdateTransactionCommand(
                                request.version(),
                                request.categoryId(),
                                request.amount(),
                                request.currency(),
                                request.exchangeRateToBase(),
                                request.transactionDate(),
                                request.description(),
                                request.transactionType()
                        )
                )
        );
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete a transaction")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Transaction deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid version"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Transaction not found"),
            @ApiResponse(responseCode = "409", description = "Version conflict")
    })
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID transactionId,
            @RequestParam @Min(0) long version
    ) {
        transactionService.delete(userId(jwt), transactionId, version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(
            summary = "List transactions",
            description = "Filters by date, category, amount, and type. Sort accepts transactionDate "
                    + "or amount with asc or desc; page size is limited to 100."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction page"),
            @ApiResponse(responseCode = "400", description = "Invalid filter, sort, or pagination"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
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
        var transactions = transactionService.list(
                userId(jwt),
                new TransactionFilter(fromDate, toDate, categoryId, minAmount, maxAmount, transactionType),
                PageRequest.of(page, size, toSort(sort))
        );
        return new PageResponse<>(
                transactions.getContent().stream().map(TransactionResponse::from).toList(),
                new PageResponse.Page(
                        transactions.getNumber(),
                        transactions.getSize(),
                        transactions.getTotalElements(),
                        transactions.getTotalPages()
                )
        );
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
