import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ApiClientError } from "../../../shared/api/client";
import type { TransactionImportPreview } from "../../../shared/api/models";
import { useNotifications } from "../../../shared/notifications/useNotifications";
import { confirmTransactionImport, exportTransactions, previewTransactionImport, transactionQueryKey } from "../api/transactionsApi";
import type { TransactionFilters, TransactionImportColumn, TransactionImportMapping } from "../api/transactionsApi";
import styles from "./TransactionsManager.module.css";

const importColumns: Array<{ key: TransactionImportColumn; label: string; required: boolean }> = [
  { key: "categoryId", label: "Category ID", required: true },
  { key: "amount", label: "Amount", required: true },
  { key: "currency", label: "Currency", required: true },
  { key: "exchangeRateToBase", label: "Exchange rate to base currency", required: true },
  { key: "transactionDate", label: "Transaction date", required: true },
  { key: "description", label: "Description", required: false },
  { key: "transactionType", label: "Transaction type", required: true },
];

const requiredColumns = importColumns.filter((column) => column.required);

function parseCsvHeader(text: string): string[] | null {
  const headers: string[] = [];
  let value = "";
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        value += '"';
        index += 1;
      } else if (character === '"') {
        quoted = false;
      } else {
        value += character;
      }
    } else if (character === '"' && value === "") {
      quoted = true;
    } else if (character === ",") {
      headers.push(value);
      value = "";
    } else if (character === "\n" || character === "\r") {
      headers.push(value);
      return headers;
    } else {
      value += character;
    }
  }

  return quoted ? null : [...headers, value];
}

function readHeader(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => resolve(typeof reader.result === "string" ? reader.result : "");
    reader.readAsText(file.slice(0, 65_536));
  });
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiClientError ? error.apiError.message : fallback;
}

interface TransactionsCsvManagerProps {
  filters: TransactionFilters;
}

export function TransactionsCsvManager({ filters }: TransactionsCsvManagerProps) {
  const [file, setFile] = useState<File | null>(null);
  const [headers, setHeaders] = useState<string[]>([]);
  const [mapping, setMapping] = useState<TransactionImportMapping>({});
  const [preview, setPreview] = useState<TransactionImportPreview | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const exportMutation = useMutation({
    mutationFn: () => exportTransactions(filters),
    onSuccess: (csv) => {
      const url = URL.createObjectURL(csv);
      const download = document.createElement("a");
      download.href = url;
      download.download = "transactions.csv";
      document.body.append(download);
      download.click();
      setTimeout(() => {
        URL.revokeObjectURL(url);
        download.remove();
      }, 0);
    },
  });
  const previewMutation = useMutation({
    mutationFn: () => previewTransactionImport(file!, mapping),
    onSuccess: setPreview,
  });
  const confirmMutation = useMutation({
    mutationFn: () => confirmTransactionImport(file!, mapping),
    onSuccess: ({ importedCount }) => {
      void queryClient.invalidateQueries({ queryKey: transactionQueryKey });
      notify(`${importedCount} transaction${importedCount === 1 ? "" : "s"} imported.`);
      setPreview(null);
    },
  });
  const mappedHeaders = Object.values(mapping).filter((header): header is string => header !== undefined && header !== "");
  const mappingIsComplete = file !== null
    && requiredColumns.every((column) => mapping[column.key] !== undefined && mapping[column.key] !== "")
    && new Set(mappedHeaders).size === mappedHeaders.length;

  async function selectFile(selectedFile: File | undefined): Promise<void> {
    setFile(selectedFile ?? null);
    setHeaders([]);
    setMapping({});
    setPreview(null);
    setFileError(null);

    if (selectedFile === undefined) return;

    try {
      const header = parseCsvHeader(await readHeader(selectedFile));
      if (header === null || header.some((name) => name === "") || new Set(header).size !== header.length) {
        setFileError("The CSV header is invalid or too long.");
        return;
      }

      setHeaders(header);
      setMapping(Object.fromEntries(importColumns.filter((column) => header.includes(column.key)).map((column) => [column.key, column.key])));
    } catch {
      setFile(null);
      setFileError("We could not read this CSV file. Please choose another file.");
    }
  }

  return (
    <section aria-labelledby="csv-title" className={styles.csvPanel}>
      <div><h2 id="csv-title">CSV import and export</h2><p>Export the transactions matching the applied filters, or preview a CSV before importing it.</p></div>
      <div className={styles.actions}><button className={styles.secondaryButton} disabled={exportMutation.isPending} onClick={() => exportMutation.mutate()} type="button">{exportMutation.isPending ? "Exporting…" : "Export CSV"}</button></div>
      {exportMutation.isError && <p className={styles.formError} role="alert">{errorMessage(exportMutation.error, "We could not export transactions. Please try again.")}</p>}
      <div className={styles.field}><label htmlFor="csv-file">CSV file</label><input accept=".csv,text/csv" id="csv-file" onChange={(event) => void selectFile(event.target.files?.[0])} type="file" />{fileError && <p className={styles.fieldError} role="alert">{fileError}</p>}</div>
      {headers.length > 0 && <fieldset className={styles.mapping}><legend>Column mapping</legend>{importColumns.map((column) => <div className={styles.field} key={column.key}><label htmlFor={`mapping-${column.key}`}>{column.label}{column.required ? " (required)" : " (optional)"}</label><select id={`mapping-${column.key}`} onChange={(event) => { setMapping((current) => ({ ...current, [column.key]: event.target.value })); setPreview(null); }} value={mapping[column.key] ?? ""}><option value="">{column.required ? "Select a CSV column" : "Do not import"}</option>{headers.map((header) => <option disabled={mapping[column.key] !== header && mappedHeaders.includes(header)} key={header} value={header}>{header}</option>)}</select></div>)}</fieldset>}
      <div className={styles.actions}><button className={styles.primaryButton} disabled={!mappingIsComplete || previewMutation.isPending} onClick={() => previewMutation.mutate()} type="button">{previewMutation.isPending ? "Previewing…" : "Preview import"}</button><button className={styles.secondaryButton} disabled={preview === null || preview.lineErrors.length > 0 || confirmMutation.isPending} onClick={() => confirmMutation.mutate()} type="button">{confirmMutation.isPending ? "Importing…" : "Confirm import"}</button></div>
      {previewMutation.isError && <p className={styles.formError} role="alert">{errorMessage(previewMutation.error, "We could not preview this CSV. Please try again.")}</p>}
      {confirmMutation.isError && <p className={styles.formError} role="alert">{errorMessage(confirmMutation.error, "We could not import this CSV. Please try again.")}</p>}
      {preview !== null && <><h3>Preview rows</h3>{preview.rows.length === 0 ? <p className={styles.empty}>This CSV has no data rows.</p> : <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>Line</th><th>Category</th><th>Amount</th><th>Currency</th><th>Date</th><th>Type</th></tr></thead><tbody>{preview.rows.map((row) => <tr key={row.lineNumber}><td>{row.lineNumber}</td><td>{row.categoryId}</td><td>{row.amount}</td><td>{row.currency}</td><td>{row.transactionDate}</td><td>{row.transactionType}</td></tr>)}</tbody></table></div>}{preview.lineErrors.length > 0 && <div className={styles.lineErrors} role="alert"><h3>Rows with errors</h3><ul>{preview.lineErrors.map((error) => <li key={`${error.lineNumber}-${error.field}-${error.code}`}>Line {error.lineNumber}, {error.field}: {error.message}</li>)}</ul></div>}</>}
    </section>
  );
}
