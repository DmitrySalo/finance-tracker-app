import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useForm, useWatch } from "react-hook-form";
import type { UseFormSetError } from "react-hook-form";
import { z } from "zod";
import { ApiClientError } from "../../../shared/api/client";
import type { Category, Transaction } from "../../../shared/api/models";
import { useNotifications } from "../../../shared/notifications/useNotifications";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";
import {
  createTransaction,
  deleteTransaction,
  listTransactions,
  transactionQueryKey,
  updateTransaction,
} from "../api/transactionsApi";
import type { TransactionFilters, TransactionInput, TransactionSort } from "../api/transactionsApi";
import { categoryQueryKey, listCategories } from "../../categories/api/categoriesApi";
import styles from "./TransactionsManager.module.css";
import { TransactionsCsvManager } from "./TransactionsCsvManager";

function transactionSchema(l: (english: string, russian: string) => string) { return z.object({
  categoryId: z.string().uuid(l("Enter a valid category ID.", "Введите корректный идентификатор категории.")),
  amount: z.string().regex(/^\d{1,15}(?:\.\d{1,4})?$/, l("Enter a positive amount with up to 4 decimal places.", "Введите положительную сумму не более чем с 4 знаками после запятой.")).refine((value) => Number(value) > 0, l("Enter a positive amount.", "Введите положительную сумму.")),
  currency: z.string().regex(/^[A-Z]{3}$/, l("Use a three-letter uppercase currency code.", "Используйте трёхбуквенный код валюты в верхнем регистре.")),
  exchangeRateToBase: z.string().regex(/^\d{1,11}(?:\.\d{1,8})?$/, l("Enter a positive exchange rate with up to 8 decimal places.", "Введите положительный курс не более чем с 8 знаками после запятой.")).refine((value) => Number(value) > 0, l("Enter a positive exchange rate.", "Введите положительный курс.")),
  transactionDate: z.string().date(l("Enter a transaction date.", "Введите дату операции.")),
  description: z.string().max(1000, l("Use no more than 1000 characters.", "Используйте не более 1000 символов.")),
  transactionType: z.enum(["INCOME", "EXPENSE"]),
}); }

type TransactionFormValues = z.infer<ReturnType<typeof transactionSchema>>;

const initialFilters: TransactionFilters = { fromDate: "", toDate: "", categoryId: "", minAmount: "", maxAmount: "", transactionType: "" };
const initialValues: TransactionFormValues = { categoryId: "", amount: "", currency: "USD", exchangeRateToBase: "1", transactionDate: "", description: "", transactionType: "EXPENSE" };

async function listAllCategories(signal?: AbortSignal): Promise<Category[]> {
  const categories: Category[] = [];
  let page = 0;
  do {
    const response = await listCategories(page, signal, 100);
    categories.push(...response.items);
    page += 1;
    if (page >= response.page.totalPages) return categories;
  } while (!signal?.aborted);
  return categories;
}

function formValues(transaction: Transaction): TransactionFormValues {
  return { categoryId: transaction.categoryId, amount: String(transaction.amount), currency: transaction.currency, exchangeRateToBase: String(transaction.exchangeRateToBase), transactionDate: transaction.transactionDate, description: transaction.description ?? "", transactionType: transaction.transactionType };
}

function applyServerViolations(error: unknown, setError: UseFormSetError<TransactionFormValues>, l: (english: string, russian: string) => string): void {
  if (!(error instanceof ApiClientError) || error.apiError.code !== "VALIDATION_FAILED") return;
  error.apiError.violations.forEach((violation) => {
    if (violation.field === "categoryId" || violation.field === "amount" || violation.field === "currency" || violation.field === "exchangeRateToBase" || violation.field === "transactionDate" || violation.field === "description" || violation.field === "transactionType") {
      setError(violation.field, { type: "server", message: l("The transaction contains an invalid value.", "Операция содержит недопустимое значение.") });
    }
  });
}

interface TransactionFormProps {
  transaction: Transaction | null;
  onCancel: () => void;
  onSaved: () => void;
}

function TransactionForm({ transaction, onCancel, onSaved }: TransactionFormProps) {
  const { l } = useLocalization();
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const { control, formState: { errors, isSubmitting }, handleSubmit, register, setError } = useForm<TransactionFormValues>({
    defaultValues: transaction === null ? initialValues : formValues(transaction),
    resolver: zodResolver(transactionSchema(l)),
  });
  const categoriesQuery = useQuery({ queryKey: [...categoryQueryKey, "all"], queryFn: ({ signal }) => listAllCategories(signal) });
  const transactionType = useWatch({ control, name: "transactionType" });
  const categories = categoriesQuery.data?.filter((category) => category.transactionType === transactionType) ?? [];

  const mutation = useMutation({
    mutationFn: (input: TransactionInput) => transaction === null ? createTransaction(input) : updateTransaction(transaction, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: transactionQueryKey });
      notify(transaction === null ? l("Transaction created.", "Операция создана.") : l("Transaction updated.", "Операция обновлена."));
      onSaved();
    },
  });

  async function onSubmit(values: TransactionFormValues): Promise<void> {
    try {
      await mutation.mutateAsync({ ...values, description: values.description === "" ? transaction?.description ?? null : values.description });
    } catch (error) {
      applyServerViolations(error, setError, l);
    }
  }

  return <form className={styles.form} noValidate onSubmit={handleSubmit(onSubmit)}>
    <h2>{transaction === null ? l("New transaction", "Новая операция") : l("Edit transaction", "Изменить операцию")}</h2>
    <div className={styles.field}><label htmlFor="transaction-category">{l("Category", "Категория")}</label><select aria-describedby={errors.categoryId ? "transaction-category-error" : undefined} aria-invalid={Boolean(errors.categoryId)} id="transaction-category" {...register("categoryId")}><option value="">{l("Select a category", "Выберите категорию")}</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.icon} {category.name}</option>)}</select>{categoriesQuery.isError && <p className={styles.fieldError} role="alert">{l("We could not load categories. Please try again.", "Не удалось загрузить категории. Попробуйте снова.")}</p>}{errors.categoryId && <p className={styles.fieldError} id="transaction-category-error" role="alert">{errors.categoryId.message}</p>}</div>
    <FormField error={errors.amount?.message} label={l("Amount", "Сумма")} name="amount" register={register} type="text" />
    <FormField error={errors.currency?.message} label={l("Currency", "Валюта")} name="currency" register={register} type="text" />
    <FormField error={errors.exchangeRateToBase?.message} label={l("Exchange rate to base currency", "Курс к основной валюте")} name="exchangeRateToBase" register={register} type="text" />
    <FormField error={errors.transactionDate?.message} label={l("Date", "Дата")} name="transactionDate" register={register} type="date" />
    <div className={styles.field}><label htmlFor="transaction-type">{l("Type", "Тип")}</label><select id="transaction-type" {...register("transactionType")}><option value="EXPENSE">{l("Expense", "Расход")}</option><option value="INCOME">{l("Income", "Доход")}</option></select></div>
    <FormField error={errors.description?.message} label={l("Description", "Описание")} name="description" register={register} type="text" />
    {mutation.isError && !(mutation.error instanceof ApiClientError && mutation.error.apiError.code === "VALIDATION_FAILED") && <p className={styles.formError} role="alert">{l("We could not save the transaction. Please try again.", "Не удалось сохранить операцию. Попробуйте снова.")}</p>}
    <div className={styles.actions}><button className={styles.primaryButton} disabled={isSubmitting} type="submit">{isSubmitting ? l("Saving…", "Сохранение…") : l("Save transaction", "Сохранить операцию")}</button><button className={styles.secondaryButton} disabled={isSubmitting} onClick={onCancel} type="button">{l("Cancel", "Отмена")}</button></div>
  </form>;
}

function FormField({ error, label, name, register, type }: { error: string | undefined; label: string; name: "categoryId" | "amount" | "currency" | "exchangeRateToBase" | "transactionDate" | "description"; register: ReturnType<typeof useForm<TransactionFormValues>>["register"]; type: string }) {
  const id = `transaction-${name}`;
  return <div className={styles.field}><label htmlFor={id}>{label}</label><input aria-describedby={error ? `${id}-error` : undefined} aria-invalid={Boolean(error)} id={id} type={type} {...register(name)} />{error && <p className={styles.fieldError} id={`${id}-error`} role="alert">{error}</p>}</div>;
}

interface TransactionRowsProps {
  transactions: Transaction[];
  onEdit: (transaction: Transaction) => void;
  onDelete: (transaction: Transaction) => void;
}

function DeleteConfirmation({ transaction, onCancel, onDeleted }: { transaction: Transaction; onCancel: () => void; onDeleted: () => void }) {
  const { l } = useLocalization();
  const cancelButton = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLElement>(null);
  const mutation = useMutation({ mutationFn: deleteTransaction, onSuccess: onDeleted });

  useEffect(() => {
    cancelButton.current?.focus();
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape" && !mutation.isPending) {
        event.preventDefault();
        onCancel();
        return;
      }
      if (event.key !== "Tab" || dialog.current === null) return;
      const buttons = dialog.current.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
      const first = buttons[0];
      const last = buttons[buttons.length - 1];
      if (first === undefined || last === undefined) return;
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [mutation.isPending, onCancel]);

  return <div className={styles.dialogBackdrop}><section aria-describedby="delete-transaction-description" aria-labelledby="delete-transaction-title" aria-modal="true" className={styles.dialog} ref={dialog} role="dialog" tabIndex={-1}><h2 id="delete-transaction-title">{l("Delete transaction?", "Удалить операцию?")}</h2><p id="delete-transaction-description">{l("Delete this transaction? This action cannot be undone.", "Удалить эту операцию? Это действие нельзя отменить.")}</p>{mutation.isError && <p className={styles.formError} role="alert">{l("We could not delete the transaction. Please try again.", "Не удалось удалить операцию. Попробуйте снова.")}</p>}<div className={styles.actions}><button className={styles.dangerButton} disabled={mutation.isPending} onClick={() => mutation.mutate(transaction)} type="button">{mutation.isPending ? l("Deleting…", "Удаление…") : l("Delete transaction", "Удалить операцию")}</button><button className={styles.secondaryButton} disabled={mutation.isPending} onClick={onCancel} ref={cancelButton} type="button">{l("Cancel", "Отмена")}</button></div></section></div>;
}

function useMobileLayout(): boolean {
  return useSyncExternalStore(
    (onStoreChange) => {
      if (typeof window.matchMedia !== "function") return () => undefined;
      const query = window.matchMedia("(max-width: 40rem)");
      query.addEventListener("change", onStoreChange);
      return () => query.removeEventListener("change", onStoreChange);
    },
    () => typeof window.matchMedia === "function" && window.matchMedia("(max-width: 40rem)").matches,
    () => false,
  );
}

function TransactionRows({ transactions, onEdit, onDelete }: TransactionRowsProps) {
  const { l } = useLocalization();
  const isMobileLayout = useMobileLayout();
  function actions(transaction: Transaction) {
    return <div className={styles.rowActions}><button onClick={() => onEdit(transaction)} type="button">{l("Edit transaction", "Изменить операцию")}</button><button onClick={() => onDelete(transaction)} type="button">{l("Delete transaction", "Удалить операцию")}</button></div>;
  }

  if (isMobileLayout) return <ul aria-label={l("Transaction cards", "Карточки операций")} className={styles.cards}>{transactions.map((transaction) => <li className={styles.card} key={transaction.id}><div className={styles.cardHeader}><strong>{transaction.amount} {transaction.currency}</strong><span>{transaction.transactionDate}</span></div><p className={styles.cardDetails}>{transaction.transactionType === "EXPENSE" ? l("Expense", "Расход") : l("Income", "Доход")} · {transaction.categoryId}</p>{transaction.description && <p className={styles.cardDetails}>{transaction.description}</p>}{actions(transaction)}</li>)}</ul>;
  return <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>{l("Date", "Дата")}</th><th>{l("Type", "Тип")}</th><th>{l("Amount", "Сумма")}</th><th>{l("Category", "Категория")}</th><th>{l("Description", "Описание")}</th><th>{l("Actions", "Действия")}</th></tr></thead><tbody>{transactions.map((transaction) => <tr key={transaction.id}><td>{transaction.transactionDate}</td><td>{transaction.transactionType === "EXPENSE" ? l("Expense", "Расход") : l("Income", "Доход")}</td><td>{transaction.amount} {transaction.currency}</td><td>{transaction.categoryId}</td><td>{transaction.description ?? "—"}</td><td>{actions(transaction)}</td></tr>)}</tbody></table></div>;
}

export function TransactionsManager() {
  const { l } = useLocalization();
  const [filters, setFilters] = useState(initialFilters);
  const [draftFilters, setDraftFilters] = useState(initialFilters);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<TransactionSort>("transactionDate,desc");
  const [editedTransaction, setEditedTransaction] = useState<Transaction | null | undefined>(undefined);
  const [deletedTransaction, setDeletedTransaction] = useState<Transaction | null>(null);
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const transactionsQuery = useQuery({ queryKey: [...transactionQueryKey, filters, page, sort], queryFn: ({ signal }) => listTransactions(filters, page, sort, signal) });
  const categoriesQuery = useQuery({ queryKey: [...categoryQueryKey, "all"], queryFn: ({ signal }) => listAllCategories(signal) });
  function changeFilter(name: keyof TransactionFilters, value: string): void {
    setDraftFilters((current) => ({ ...current, [name]: value }));
  }

  return <section className={styles.page}>
    <header className={styles.header}><div><h1>{l("Transactions", "Операции")}</h1><p>{l("Review, filter, and manage your income and expenses.", "Просматривайте, фильтруйте и управляйте доходами и расходами.")}</p></div><div className={styles.headerActions}><button className={styles.secondaryButton} onClick={() => { setDraftFilters(initialFilters); setFilters(initialFilters); setPage(0); }} type="button">{l("Reset filters", "Сбросить фильтры")}</button><button className={styles.primaryButton} onClick={() => setEditedTransaction(null)} type="button">{l("Add transaction", "Добавить операцию")}</button></div></header>
    {editedTransaction !== undefined && <TransactionForm key={editedTransaction?.id ?? "new"} onCancel={() => setEditedTransaction(undefined)} onSaved={() => setEditedTransaction(undefined)} transaction={editedTransaction} />}
    <form className={styles.filterPanel} onSubmit={(event) => { event.preventDefault(); setFilters(draftFilters); setPage(0); }}><h2>{l("Filters", "Фильтры")}</h2><div className={styles.filters}>
      <FilterField label={l("From date", "С даты")} name="fromDate" onChange={changeFilter} type="date" value={draftFilters.fromDate} />
      <FilterField label={l("To date", "По дату")} name="toDate" onChange={changeFilter} type="date" value={draftFilters.toDate} />
      <div className={styles.field}><label htmlFor="filter-category">{l("Category", "Категория")}</label><select id="filter-category" onChange={(event) => changeFilter("categoryId", event.target.value)} value={draftFilters.categoryId}><option value="">{l("All categories", "Все категории")}</option>{categoriesQuery.data?.map((category) => <option key={category.id} value={category.id}>{category.icon} {category.name}</option>)}</select></div>
      <FilterField label={l("Minimum amount", "Минимальная сумма")} name="minAmount" onChange={changeFilter} type="text" value={draftFilters.minAmount} />
      <FilterField label={l("Maximum amount", "Максимальная сумма")} name="maxAmount" onChange={changeFilter} type="text" value={draftFilters.maxAmount} />
      <div className={styles.field}><label htmlFor="filter-type">{l("Type", "Тип")}</label><select id="filter-type" onChange={(event) => changeFilter("transactionType", event.target.value)} value={draftFilters.transactionType}><option value="">{l("All types", "Все типы")}</option><option value="EXPENSE">{l("Expense", "Расход")}</option><option value="INCOME">{l("Income", "Доход")}</option></select></div><button className={styles.primaryButton} type="submit">{l("Apply filters", "Применить фильтры")}</button>
    </div></form>
    <TransactionsCsvManager filters={filters} />
    <div className={styles.field}><label htmlFor="sort">{l("Sort by", "Сортировать")}</label><select id="sort" onChange={(event) => { setSort(event.target.value as TransactionSort); setPage(0); }} value={sort}><option value="transactionDate,desc">{l("Date: newest first", "Дата: сначала новые")}</option><option value="transactionDate,asc">{l("Date: oldest first", "Дата: сначала старые")}</option><option value="amount,desc">{l("Amount: highest first", "Сумма: сначала большие")}</option><option value="amount,asc">{l("Amount: lowest first", "Сумма: сначала маленькие")}</option></select></div>
    {transactionsQuery.isPending && <p role="status">{l("Loading transactions…", "Загрузка операций…")}</p>}
    {transactionsQuery.isError && <p className={styles.formError} role="alert">{l("We could not load transactions. Please refresh the page.", "Не удалось загрузить операции. Обновите страницу.")}</p>}
    {transactionsQuery.data?.items.length === 0 && <p className={styles.empty}>{l("No transactions match these filters.", "Нет операций, соответствующих этим фильтрам.")}</p>}
    {transactionsQuery.data !== undefined && transactionsQuery.data.items.length > 0 && <TransactionRows onDelete={setDeletedTransaction} onEdit={setEditedTransaction} transactions={transactionsQuery.data.items} />}
    {transactionsQuery.data !== undefined && transactionsQuery.data.page.totalPages > 1 && <nav aria-label={l("Transaction pages", "Страницы операций")} className={styles.pagination}><button disabled={page === 0} onClick={() => setPage((current) => current - 1)} type="button">{l("Previous page", "Предыдущая страница")}</button><span>{l(`Page ${page + 1} of ${transactionsQuery.data.page.totalPages}`, `Страница ${page + 1} из ${transactionsQuery.data.page.totalPages}`)}</span><button disabled={page + 1 === transactionsQuery.data.page.totalPages} onClick={() => setPage((current) => current + 1)} type="button">{l("Next page", "Следующая страница")}</button></nav>}
    {deletedTransaction !== null && <DeleteConfirmation onCancel={() => setDeletedTransaction(null)} onDeleted={() => { void queryClient.invalidateQueries({ queryKey: transactionQueryKey }); notify(l("Transaction deleted.", "Операция удалена.")); if (transactionsQuery.data?.items.length === 1 && page > 0) setPage((current) => current - 1); setDeletedTransaction(null); }} transaction={deletedTransaction} />}
  </section>;
}

function FilterField({ label, name, onChange, type, value }: { label: string; name: "fromDate" | "toDate" | "categoryId" | "minAmount" | "maxAmount"; onChange: (name: keyof TransactionFilters, value: string) => void; type: string; value: string }) {
  const id = `filter-${name}`;
  return <div className={styles.field}><label htmlFor={id}>{label}</label><input id={id} onChange={(event) => onChange(name, event.target.value)} type={type} value={value} /></div>;
}
