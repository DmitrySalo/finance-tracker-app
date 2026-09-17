import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import { Controller, useForm } from "react-hook-form";
import type { UseFormSetError } from "react-hook-form";
import { z } from "zod";
import { categoryQueryKey, listCategories } from "../../categories/api/categoriesApi";
import { ApiClientError } from "../../../shared/api/client";
import type { Budget, Category } from "../../../shared/api/models";
import { useNotifications } from "../../../shared/notifications/useNotifications";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";
import { formatDecimal } from "../../../shared/formatters/formatDecimal";
import { getCurrentUser } from "../../auth/api/authApi";
import { budgetQueryKey, createBudget, deleteBudget, listAllBudgets, updateBudget } from "../api/budgetsApi";
import { LocalizedDatePicker } from "../../../shared/ui/LocalizedDatePicker";
import type { BudgetInput } from "../api/budgetsApi";
import styles from "./BudgetsManager.module.css";

function budgetSchema(l: (english: string, russian: string) => string) { return z.object({
  categoryId: z.string().uuid(l("Select an expense category.", "Выберите категорию расходов.")),
  budgetMonth: z.string().regex(/^\d{4}-\d{2}$/, l("Select a budget month.", "Выберите месяц бюджета.")),
  limitAmount: z.string().regex(/^\d{1,15}(?:\.\d{1,4})?$/, l("Enter a positive amount with up to 4 decimal places.", "Введите положительную сумму не более чем с 4 знаками после запятой.")).refine((value) => Number(value) > 0, l("Enter a positive amount.", "Введите положительную сумму.")),
  currency: z.string().regex(/^[A-Z]{3}$/, l("Use a three-letter uppercase currency code.", "Используйте трёхбуквенный код валюты в верхнем регистре.")),
}); }

type BudgetFormValues = z.infer<ReturnType<typeof budgetSchema>>;

function currentMonth(): string {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
}

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

function formValues(budget: Budget): BudgetFormValues {
  return { categoryId: budget.categoryId, budgetMonth: budget.budgetMonth.slice(0, 7), limitAmount: budget.limitAmount, currency: budget.currency };
}

function toBudgetInput(values: BudgetFormValues): BudgetInput {
  return { ...values, budgetMonth: `${values.budgetMonth}-01` };
}

function applyServerViolations(error: unknown, setError: UseFormSetError<BudgetFormValues>, l: (english: string, russian: string) => string): void {
  if (!(error instanceof ApiClientError) || error.apiError.code !== "VALIDATION_FAILED") return;
  error.apiError.violations.forEach((violation) => {
    if (violation.field === "categoryId" || violation.field === "budgetMonth" || violation.field === "limitAmount" || violation.field === "currency") {
      setError(violation.field, { type: "server", message: l("The budget contains an invalid value.", "Бюджет содержит недопустимое значение.") });
    }
  });
}

function errorMessage(_error: unknown, operation: "delete" | "save", l: (english: string, russian: string) => string): string {
  return operation === "delete" ? l("We could not delete the budget. Please try again.", "Не удалось удалить бюджет. Попробуйте снова.") : l("We could not save the budget. Please try again.", "Не удалось сохранить бюджет. Попробуйте снова.");
}

interface BudgetFormProps {
  budget: Budget | null;
  categories: Category[];
  categoriesFailed: boolean;
  defaultMonth: string;
  onCancel: () => void;
  onSaved: () => void;
}

function BudgetForm({ budget, categories, categoriesFailed, defaultMonth, onCancel, onSaved }: BudgetFormProps) {
  const { l } = useLocalization();
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const currentUserQuery = useQuery({
    enabled: budget === null,
    queryKey: ["current-user"],
    queryFn: ({ signal }) => getCurrentUser(signal),
  });
  const { control, formState: { errors, isSubmitting }, handleSubmit, register, setError, setValue } = useForm<BudgetFormValues>({
    defaultValues: budget === null ? { categoryId: "", budgetMonth: defaultMonth, limitAmount: "", currency: "" } : formValues(budget),
    resolver: zodResolver(budgetSchema(l)),
  });
  const mutation = useMutation({
    mutationFn: (values: BudgetFormValues) => budget === null ? createBudget(toBudgetInput(values)) : updateBudget(budget, toBudgetInput(values)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: budgetQueryKey });
      notify(budget === null ? l("Budget created.", "Бюджет создан.") : l("Budget updated.", "Бюджет обновлён."));
      onSaved();
    },
  });

  async function onSubmit(values: BudgetFormValues): Promise<void> {
    if (budget === null && currentUserQuery.data === undefined) return;

    try {
      await mutation.mutateAsync(values);
    } catch (error) {
      applyServerViolations(error, setError, l);
    }
  }

  useEffect(() => {
    if (budget === null && currentUserQuery.data !== undefined) {
      setValue("currency", currentUserQuery.data.baseCurrency, { shouldValidate: true });
    }
  }, [budget, currentUserQuery.data, setValue]);

  const expenseCategories = categories.filter((category) => category.transactionType === "EXPENSE");
  return <form className={styles.form} noValidate onSubmit={handleSubmit(onSubmit)}>
    <h2>{budget === null ? l("New budget", "Новый бюджет") : l("Edit budget", "Изменить бюджет")}</h2>
    <div className={styles.field}><label htmlFor="budget-category">{l("Expense category", "Категория расходов")}</label><select aria-describedby={errors.categoryId ? "budget-category-error" : undefined} aria-invalid={Boolean(errors.categoryId)} id="budget-category" {...register("categoryId")}><option value="">{l("Select an expense category", "Выберите категорию расходов")}</option>{expenseCategories.map((category) => <option key={category.id} value={category.id}>{category.icon} {category.name}</option>)}</select>{categoriesFailed && <p className={styles.fieldError} role="alert">{l("We could not load expense categories. Please try again.", "Не удалось загрузить категории расходов. Попробуйте снова.")}</p>}{errors.categoryId && <p className={styles.fieldError} id="budget-category-error" role="alert">{errors.categoryId.message}</p>}</div>
    <div className={styles.field}><label htmlFor="budget-budgetMonth">{l("Month", "Месяц")}</label><Controller control={control} name="budgetMonth" render={({ field }) => <LocalizedDatePicker aria-describedby={errors.budgetMonth ? "budget-budgetMonth-error" : undefined} aria-invalid={Boolean(errors.budgetMonth)} id="budget-budgetMonth" name={field.name} onBlur={field.onBlur} onChange={field.onChange} ref={field.ref} type="month" value={field.value} />} />{errors.budgetMonth && <p className={styles.fieldError} id="budget-budgetMonth-error" role="alert">{errors.budgetMonth.message}</p>}</div>
    <Field error={errors.limitAmount?.message} label={l("Limit amount", "Лимит")} name="limitAmount" register={register} type="text" />
    <Field error={errors.currency?.message} label={l("Currency", "Валюта")} name="currency" readOnly register={register} type="text" />
    {budget === null && currentUserQuery.isPending && <p role="status">{l("Loading your base currency…", "Загрузка основной валюты…")}</p>}
    {budget === null && currentUserQuery.isError && <p className={styles.formError} role="alert">{l("We could not load your base currency. Please try again.", "Не удалось загрузить основную валюту. Попробуйте снова.")}</p>}
    {mutation.isError && !(mutation.error instanceof ApiClientError && mutation.error.apiError.code === "VALIDATION_FAILED") && <p className={styles.formError} role="alert">{errorMessage(mutation.error, "save", l)}</p>}
    <div className={styles.actions}><button className={styles.primaryButton} disabled={isSubmitting || (budget === null && currentUserQuery.data === undefined)} type="submit">{isSubmitting ? l("Saving…", "Сохранение…") : l("Save budget", "Сохранить бюджет")}</button><button className={styles.secondaryButton} disabled={isSubmitting} onClick={onCancel} type="button">{l("Cancel", "Отмена")}</button></div>
  </form>;
}

function Field({ error, label, name, readOnly = false, register, type }: { error: string | undefined; label: string; name: "budgetMonth" | "limitAmount" | "currency"; readOnly?: boolean; register: ReturnType<typeof useForm<BudgetFormValues>>["register"]; type: "month" | "text" }) {
  const id = `budget-${name}`;
  return <div className={styles.field}><label htmlFor={id}>{label}</label><input aria-describedby={error ? `${id}-error` : undefined} aria-invalid={Boolean(error)} id={id} readOnly={readOnly} type={type} {...register(name)} />{error && <p className={styles.fieldError} id={`${id}-error`} role="alert">{error}</p>}</div>;
}

function BudgetProgress({ budget }: { budget: Budget }) {
  const { l } = useLocalization();
  const displayedPercentage = formatDecimal(budget.percentage);
  const displayedSpentAmount = formatDecimal(budget.spentAmount);
  const displayedLimitAmount = formatDecimal(budget.limitAmount);
  const displayedRemainingAmount = formatDecimal(budget.remainingAmount);
  const percentage = Number(budget.percentage);
  const progress = Number.isFinite(percentage) ? Math.max(0, percentage) : 0;
  const displayedProgress = Math.min(progress, 100);
  const accessibleText = l(`${displayedPercentage}% of budget used. Spent ${displayedSpentAmount} ${budget.currency} of ${displayedLimitAmount} ${budget.currency}; ${displayedRemainingAmount} ${budget.currency} remaining.`, `Использовано ${displayedPercentage}% бюджета. Потрачено ${displayedSpentAmount} ${budget.currency} из ${displayedLimitAmount} ${budget.currency}; осталось ${displayedRemainingAmount} ${budget.currency}.`);
  const status = progress > 100 ? l("Over budget", "Бюджет превышен") : progress === 100 ? l("Budget reached", "Бюджет исчерпан") : l("Budget available", "Бюджет доступен");

  return <div className={styles.progressGroup}>
    <div aria-label={l("Budget progress", "Прогресс бюджета")} aria-valuemax={100} aria-valuemin={0} aria-valuenow={displayedProgress} aria-valuetext={accessibleText} className={styles.progress} role="progressbar"><span className={progress > 100 ? styles.progressFillOver : styles.progressFill} style={{ width: `${displayedProgress}%` }} /></div>
    <p className={styles.values}>{l(`${displayedSpentAmount} ${budget.currency} spent of ${displayedLimitAmount} ${budget.currency} · ${displayedRemainingAmount} ${budget.currency} remaining ·`, `${displayedSpentAmount} ${budget.currency} из ${displayedLimitAmount} ${budget.currency} потрачено · ${displayedRemainingAmount} ${budget.currency} осталось ·`)} <strong>{status} ({displayedPercentage}%)</strong></p>
  </div>;
}

interface DeleteConfirmationProps {
  budget: Budget;
  onCancel: () => void;
  onDeleted: () => void;
  returnFocusTo: RefObject<HTMLButtonElement | null>;
}

function DeleteConfirmation({ budget, onCancel, onDeleted, returnFocusTo }: DeleteConfirmationProps) {
  const { l } = useLocalization();
  const cancelButton = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLElement>(null);
  const deletedSuccessfully = useRef(false);
  const mutation = useMutation({ mutationFn: () => deleteBudget(budget), onSuccess: () => { deletedSuccessfully.current = true; onDeleted(); } });

  useEffect(() => {
    const returnFocusElement = returnFocusTo.current;
    cancelButton.current?.focus();
    return () => {
      if (!deletedSuccessfully.current) returnFocusElement?.focus();
    };
  }, [returnFocusTo]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape") {
        if (mutation.isPending) return;
        event.preventDefault();
        onCancel();
        return;
      }
      if (event.key !== "Tab" || dialog.current === null) return;

      if (mutation.isPending) {
        event.preventDefault();
        dialog.current.focus();
        return;
      }

      const buttons = dialog.current.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
      const first = buttons[0];
      const last = buttons[buttons.length - 1];
      if (first === undefined || last === undefined) return;
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [mutation.isPending, onCancel]);

  return <div className={styles.dialogBackdrop}><section aria-describedby="delete-budget-description" aria-labelledby="delete-budget-title" aria-modal="true" className={styles.dialog} ref={dialog} role="dialog" tabIndex={-1}><h2 id="delete-budget-title">{l("Delete budget?", "Удалить бюджет?")}</h2><p id="delete-budget-description">{l("Delete this budget? This action cannot be undone.", "Удалить этот бюджет? Это действие нельзя отменить.")}</p>{mutation.isError && <p className={styles.formError} role="alert">{errorMessage(mutation.error, "delete", l)}</p>}<div className={styles.actions}><button className={styles.dangerButton} disabled={mutation.isPending} onClick={() => mutation.mutate()} type="button">{mutation.isPending ? l("Deleting…", "Удаление…") : l("Delete budget", "Удалить бюджет")}</button><button className={styles.secondaryButton} disabled={mutation.isPending} onClick={onCancel} ref={cancelButton} type="button">{l("Cancel", "Отмена")}</button></div></section></div>;
}

export function BudgetsManager() {
  const { l } = useLocalization();
  const [selectedMonth, setSelectedMonth] = useState(currentMonth);
  const [editedBudget, setEditedBudget] = useState<Budget | null | undefined>(undefined);
  const [deletedBudget, setDeletedBudget] = useState<Budget | null>(null);
  const addButton = useRef<HTMLButtonElement>(null);
  const deleteButton = useRef<HTMLButtonElement>(null);
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const budgetsQuery = useQuery({ queryKey: budgetQueryKey, queryFn: ({ signal }) => listAllBudgets(signal) });
  const categoriesQuery = useQuery({ queryKey: [...categoryQueryKey, "all"], queryFn: ({ signal }) => listAllCategories(signal) });
  const budgets = budgetsQuery.data?.filter((budget) => budget.budgetMonth.slice(0, 7) === selectedMonth) ?? [];

  return <section className={styles.page}>
    <header className={styles.header}><div><h1>{l("Budgets", "Бюджеты")}</h1><p>{l("Set monthly spending limits for your expense categories.", "Устанавливайте месячные лимиты расходов по категориям.")}</p></div><button className={styles.primaryButton} onClick={() => setEditedBudget(null)} ref={addButton} type="button">{l("Add budget", "Добавить бюджет")}</button></header>
    <div className={styles.monthPicker}><label htmlFor="budget-month-picker">{l("Month", "Месяц")}</label><LocalizedDatePicker id="budget-month-picker" onChange={setSelectedMonth} type="month" value={selectedMonth} /></div>
    {editedBudget !== undefined && <BudgetForm budget={editedBudget} categories={categoriesQuery.data ?? []} categoriesFailed={categoriesQuery.isError} defaultMonth={selectedMonth} key={editedBudget?.id ?? "new"} onCancel={() => setEditedBudget(undefined)} onSaved={() => setEditedBudget(undefined)} />}
    {budgetsQuery.isPending && <p role="status">{l("Loading budgets…", "Загрузка бюджетов…")}</p>}
    {budgetsQuery.isError && <p className={styles.formError} role="alert">{l("We could not load budgets. Please refresh the page.", "Не удалось загрузить бюджеты. Обновите страницу.")}</p>}
    {budgetsQuery.data !== undefined && budgets.length === 0 && <p className={styles.empty}>{l("No budgets for this month. Add one to track your spending.", "На этот месяц нет бюджетов. Добавьте бюджет для отслеживания расходов.")}</p>}
    {budgets.length > 0 && <ul className={styles.list}>{budgets.map((budget) => {
      const category = categoriesQuery.data?.find((item) => item.id === budget.categoryId);
       return <li className={styles.budget} key={budget.id}><div className={styles.budgetHeader}><div><strong>{category ? `${category.icon} ${category.name}` : l("Expense category", "Категория расходов")}</strong><p className={styles.values}>{budget.budgetMonth}</p></div><div className={styles.rowActions}><button onClick={() => setEditedBudget(budget)} type="button">{l("Edit budget", "Изменить бюджет")}</button><button onClick={(event) => { deleteButton.current = event.currentTarget; setDeletedBudget(budget); }} type="button">{l("Delete budget", "Удалить бюджет")}</button></div></div><BudgetProgress budget={budget} /></li>;
    })}</ul>}
    {deletedBudget !== null && <DeleteConfirmation budget={deletedBudget} onCancel={() => setDeletedBudget(null)} onDeleted={() => { void queryClient.invalidateQueries({ queryKey: budgetQueryKey }); notify(l("Budget deleted.", "Бюджет удалён.")); setDeletedBudget(null); requestAnimationFrame(() => addButton.current?.focus()); }} returnFocusTo={deleteButton} />}
  </section>;
}
