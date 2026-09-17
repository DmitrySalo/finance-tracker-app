import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import { useForm } from "react-hook-form";
import type { UseFormSetError } from "react-hook-form";
import { z } from "zod";
import { ApiClientError } from "../../../shared/api/client";
import type { Category } from "../../../shared/api/models";
import { useNotifications } from "../../../shared/notifications/useNotifications";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";
import {
  categoryQueryKey,
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
} from "../api/categoriesApi";
import styles from "./CategoriesManager.module.css";

function categorySchema(l: (english: string, russian: string) => string) { return z.object({
  name: z.string().trim().min(1, l("Enter a category name.", "Введите название категории.")).max(100, l("Use no more than 100 characters.", "Используйте не более 100 символов.")),
  transactionType: z.enum(["INCOME", "EXPENSE"]),
  icon: z.string().min(1),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/, l("Choose a valid color.", "Выберите корректный цвет.")),
}); }

type CategoryFormValues = z.infer<ReturnType<typeof categorySchema>>;

const iconOptions = ["🏠", "🛒", "🚗", "🍽️", "💼", "🎁"];
const colorOptions = ["#2457D6", "#0F766E", "#B45309", "#B42318", "#7C3AED", "#475569"];
const initialValues: CategoryFormValues = {
  name: "",
  transactionType: "EXPENSE",
  icon: iconOptions[0],
  color: colorOptions[0],
};

function errorMessage(error: unknown, operation: "delete" | "save", l: (english: string, russian: string) => string): string {
  if (error instanceof ApiClientError) {
    if (error.apiError.code === "CONFLICT" && operation === "delete") {
      return l("This category cannot be deleted because it is in use.", "Эту категорию нельзя удалить, потому что она используется.");
    }
  }

  return operation === "delete" ? l("We could not delete the category. Please try again.", "Не удалось удалить категорию. Попробуйте снова.") : l("We could not save the category. Please try again.", "Не удалось сохранить категорию. Попробуйте снова.");
}

function applyServerViolations(error: unknown, setError: UseFormSetError<CategoryFormValues>, l: (english: string, russian: string) => string): void {
  if (!(error instanceof ApiClientError) || error.apiError.code !== "VALIDATION_FAILED") {
    return;
  }

  error.apiError.violations.forEach((violation) => {
    if (violation.field === "name" || violation.field === "transactionType" || violation.field === "icon" || violation.field === "color") {
      setError(violation.field, { type: "server", message: l("The category contains an invalid value.", "Категория содержит недопустимое значение.") });
    }
  });
}

interface CategoryFormProps {
  category: Category | null;
  onCancel: () => void;
  onSaved: () => void;
}

function CategoryForm({ category, onCancel, onSaved }: CategoryFormProps) {
  const { l } = useLocalization();
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const defaultValues = category === null ? initialValues : {
    name: category.name,
    transactionType: category.transactionType,
    icon: category.icon,
    color: category.color,
  };
  const [selectedIcon, setSelectedIcon] = useState(defaultValues.icon);
  const [selectedColor, setSelectedColor] = useState(defaultValues.color);
  const { formState: { errors, isSubmitting }, handleSubmit, register, setError, setValue } = useForm<CategoryFormValues>({
    defaultValues,
    resolver: zodResolver(categorySchema(l)),
  });
  const mutation = useMutation({
    mutationFn: (input: CategoryFormValues) => category === null ? createCategory(input) : updateCategory(category, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoryQueryKey });
      notify(category === null ? l("Category created.", "Категория создана.") : l("Category updated.", "Категория обновлена."));
      onSaved();
    },
  });
  async function onSubmit(input: CategoryFormValues) {
    try {
      await mutation.mutateAsync(input);
    } catch (error) {
      applyServerViolations(error, setError, l);
    }
  }

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit(onSubmit)}>
      <h2>{category === null ? l("New category", "Новая категория") : l("Edit category", "Изменить категорию")}</h2>
      <div className={styles.field}>
        <label htmlFor="category-name">{l("Name", "Название")}</label>
        <input aria-describedby={errors.name ? "category-name-error" : undefined} aria-invalid={Boolean(errors.name)} autoFocus id="category-name" {...register("name")} />
        {errors.name && <p className={styles.fieldError} id="category-name-error" role="alert">{errors.name.message}</p>}
      </div>
      <div className={styles.field}>
        <label htmlFor="category-type">{l("Type", "Тип")}</label>
        <select id="category-type" {...register("transactionType")}>
          <option value="EXPENSE">{l("Expense", "Расход")}</option>
          <option value="INCOME">{l("Income", "Доход")}</option>
        </select>
      </div>
      <fieldset className={styles.selector}>
        <legend>{l("Icon", "Значок")}</legend>
        <div className={styles.options}>
          {iconOptions.map((icon) => (
            <button aria-label={l(`Choose ${icon} icon`, `Выбрать значок ${icon}`)} aria-pressed={selectedIcon === icon} className={styles.iconOption} key={icon} onClick={() => { setSelectedIcon(icon); setValue("icon", icon); }} type="button">{icon}</button>
          ))}
        </div>
        {errors.icon && <p className={styles.fieldError} role="alert">{errors.icon.message}</p>}
      </fieldset>
      <fieldset className={styles.selector}>
        <legend>{l("Color", "Цвет")}</legend>
        <div className={styles.options}>
          {colorOptions.map((color) => (
            <button aria-label={l(`Choose ${color} color`, `Выбрать цвет ${color}`)} aria-pressed={selectedColor === color} className={styles.colorOption} data-color={color} key={color} onClick={() => { setSelectedColor(color); setValue("color", color); }} type="button" />
          ))}
        </div>
        {errors.color && <p className={styles.fieldError} role="alert">{errors.color.message}</p>}
      </fieldset>
       {mutation.isError && !(mutation.error instanceof ApiClientError && mutation.error.apiError.code === "VALIDATION_FAILED") && <p className={styles.formError} role="alert">{errorMessage(mutation.error, "save", l)}</p>}
      <div className={styles.actions}>
        <button className={styles.primaryButton} disabled={isSubmitting} type="submit">{isSubmitting ? l("Saving…", "Сохранение…") : l("Save category", "Сохранить категорию")}</button>
        <button className={styles.secondaryButton} disabled={isSubmitting} onClick={onCancel} type="button">{l("Cancel", "Отмена")}</button>
      </div>
    </form>
  );
}

interface DeleteConfirmationProps {
  category: Category;
  onCancel: () => void;
  onDeleted: () => void;
  returnFocusTo: RefObject<HTMLButtonElement | null>;
}

function DeleteConfirmation({ category, onCancel, onDeleted, returnFocusTo }: DeleteConfirmationProps) {
  const { l } = useLocalization();
  const cancelButton = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLElement>(null);
  const deletedSuccessfully = useRef(false);
  const { notify } = useNotifications();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => deleteCategory(category.id),
    onSuccess: () => {
      deletedSuccessfully.current = true;
      void queryClient.invalidateQueries({ queryKey: categoryQueryKey });
       notify(l("Category deleted.", "Категория удалена."));
      onDeleted();
    },
  });
  useEffect(() => {
    const returnFocusElement = returnFocusTo.current;
    cancelButton.current?.focus();

    return () => {
      if (!deletedSuccessfully.current) {
        returnFocusElement?.focus();
      }
    };
  }, [returnFocusTo]);

  useEffect(() => {
    function keepFocusInDialog(event: KeyboardEvent): void {
      if (event.key === "Escape") {
        if (mutation.isPending) {
          return;
        }
        event.preventDefault();
        onCancel();
        return;
      }
      if (event.key !== "Tab" || dialog.current === null) {
        return;
      }

      if (mutation.isPending) {
        event.preventDefault();
        dialog.current.focus();
        return;
      }

      const focusable = dialog.current.querySelectorAll<HTMLElement>("button:not(:disabled)");
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (first === undefined || last === undefined) {
        return;
      }
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", keepFocusInDialog);
    return () => {
      document.removeEventListener("keydown", keepFocusInDialog);
    };
  }, [mutation.isPending, onCancel]);

  return (
    <div className={styles.dialogBackdrop}>
      <section aria-describedby="delete-category-description" aria-labelledby="delete-category-title" aria-modal="true" className={styles.dialog} ref={dialog} role="dialog" tabIndex={-1}>
        <h2 id="delete-category-title">{l("Delete category?", "Удалить категорию?")}</h2>
        <p id="delete-category-description">{l(`Delete “${category.name}”? This action cannot be undone.`, `Удалить «${category.name}»? Это действие нельзя отменить.`)}</p>
        {mutation.isError && <p className={styles.formError} role="alert">{errorMessage(mutation.error, "delete", l)}</p>}
        <div className={styles.actions}>
          <button className={styles.dangerButton} disabled={mutation.isPending} onClick={() => mutation.mutate()} type="button">{mutation.isPending ? l("Deleting…", "Удаление…") : l("Delete category", "Удалить категорию")}</button>
          <button className={styles.secondaryButton} disabled={mutation.isPending} onClick={onCancel} ref={cancelButton} type="button">{l("Cancel", "Отмена")}</button>
        </div>
      </section>
    </div>
  );
}

export function CategoriesManager() {
  const { l } = useLocalization();
  const [editedCategory, setEditedCategory] = useState<Category | null | undefined>(undefined);
  const [deletedCategory, setDeletedCategory] = useState<Category | null>(null);
  const [page, setPage] = useState(0);
  const addButton = useRef<HTMLButtonElement>(null);
  const deleteButton = useRef<HTMLButtonElement>(null);
  const categoriesQuery = useQuery({ queryKey: [...categoryQueryKey, page], queryFn: ({ signal }) => listCategories(page, signal) });

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>{l("Categories", "Категории")}</h1>
          <p>{l("Organize income and expenses with your own categories.", "Упорядочивайте доходы и расходы с помощью собственных категорий.")}</p>
        </div>
        <button className={styles.primaryButton} onClick={() => setEditedCategory(null)} ref={addButton} type="button">{l("Add category", "Добавить категорию")}</button>
      </header>
      {editedCategory !== undefined && <CategoryForm category={editedCategory} key={editedCategory?.id ?? "new"} onCancel={() => setEditedCategory(undefined)} onSaved={() => setEditedCategory(undefined)} />}
      {categoriesQuery.isPending && <p role="status">{l("Loading categories…", "Загрузка категорий…")}</p>}
      {categoriesQuery.isError && <p className={styles.formError} role="alert">{l("We could not load categories. Please refresh the page.", "Не удалось загрузить категории. Обновите страницу.")}</p>}
      {categoriesQuery.data?.items.length === 0 && <p className={styles.empty}>{l("No categories yet. Add one to start organizing your finances.", "Категорий пока нет. Добавьте категорию, чтобы начать учитывать финансы.")}</p>}
      {categoriesQuery.data !== undefined && categoriesQuery.data.items.length > 0 && (
        <ul className={styles.list}>
          {categoriesQuery.data.items.map((category) => (
            <li className={styles.category} key={category.id}>
              <span aria-hidden="true" className={styles.categoryColor} style={{ backgroundColor: category.color }} />
               <span aria-label={l(`${category.icon} icon`, `Значок ${category.icon}`)} className={styles.categoryIcon} role="img">{category.icon}</span>
               <span className={styles.categoryDetails}><strong>{category.name}</strong><small>{category.transactionType === "EXPENSE" ? l("Expense", "Расход") : l("Income", "Доход")}</small></span>
              <span className={styles.rowActions}>
                 <button onClick={() => setEditedCategory(category)} type="button">{l(`Edit ${category.name}`, `Изменить ${category.name}`)}</button>
                 <button onClick={(event) => { deleteButton.current = event.currentTarget; setDeletedCategory(category); }} type="button">{l(`Delete ${category.name}`, `Удалить ${category.name}`)}</button>
              </span>
            </li>
          ))}
        </ul>
      )}
      {categoriesQuery.data !== undefined && categoriesQuery.data.page.totalPages > 1 && (
        <nav aria-label={l("Category pages", "Страницы категорий")} className={styles.pagination}>
          <button disabled={page === 0} onClick={() => setPage((current) => current - 1)} type="button">{l("Previous page", "Предыдущая страница")}</button>
          <span>{l(`Page ${page + 1} of ${categoriesQuery.data.page.totalPages}`, `Страница ${page + 1} из ${categoriesQuery.data.page.totalPages}`)}</span>
          <button disabled={page + 1 === categoriesQuery.data.page.totalPages} onClick={() => setPage((current) => current + 1)} type="button">{l("Next page", "Следующая страница")}</button>
        </nav>
      )}
      {deletedCategory !== null && <DeleteConfirmation category={deletedCategory} onCancel={() => setDeletedCategory(null)} onDeleted={() => {
        if (categoriesQuery.data?.items.length === 1 && page > 0) {
          setPage(page - 1);
        }
        setDeletedCategory(null);
        requestAnimationFrame(() => addButton.current?.focus());
      }} returnFocusTo={deleteButton} />}
    </section>
  );
}
