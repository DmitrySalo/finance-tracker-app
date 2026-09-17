import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { DashboardCategoryExpense, SpendingTrend } from "../../../shared/api/models";
import { getDashboard, getSpendingTrend } from "../api/dashboardApi";
import styles from "./DashboardManager.module.css";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";

const chartPalette = ["#2457d6", "#16803c", "#b45309", "#9333ea", "#be123c"];

function currentMonth(): string {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
}

function categoryColor(category: DashboardCategoryExpense, index: number): string {
  return /^#[0-9a-f]{6}$/i.test(category.categoryColor) ? category.categoryColor : chartPalette[index % chartPalette.length];
}

function amount(value: string): number {
  const parsedValue = Number(value);
  return Number.isFinite(parsedValue) && parsedValue >= 0 ? parsedValue : 0;
}

function PieChart({ categories }: { categories: DashboardCategoryExpense[] }) {
  const { l } = useLocalization();
  const total = categories.reduce((sum, category) => sum + amount(category.amount), 0);
  const alternative = categories.map((category) => `${category.categoryName}: ${category.amount}`).join("; ");

  return <figure className={styles.chartCard}>
    <figcaption><h2>{l("Expenses by category", "Расходы по категориям")}</h2><p>{l("Monthly expense breakdown.", "Структура расходов за месяц.")}</p></figcaption>
    <svg aria-label={`${l("Expenses by category.", "Расходы по категориям.")} ${alternative}`} className={styles.pieChart} role="img" viewBox="0 0 100 100">
      {categories.map((category, index) => {
        const share = total === 0 ? 0 : amount(category.amount) / total;
        const dash = `${share * 100} ${100 - share * 100}`;
        const previousShares = categories.slice(0, index).reduce((sum, previousCategory) => sum + (total === 0 ? 0 : amount(previousCategory.amount) / total), 0);
        const offset = 25 - previousShares * 100;
        return <circle cx="50" cy="50" fill="none" key={category.categoryId} pathLength="100" r="25" stroke={categoryColor(category, index)} strokeDasharray={dash} strokeDashoffset={offset} strokeWidth="50" />;
      })}
    </svg>
    <ul className={styles.legend}>{categories.map((category, index) => <li key={category.categoryId}><span aria-hidden="true" className={styles.legendMarker} style={{ backgroundColor: categoryColor(category, index) }} />{category.categoryIcon} {category.categoryName}: {category.amount}</li>)}</ul>
  </figure>;
}

function LineChart({ trend }: { trend: SpendingTrend }) {
  const { l } = useLocalization();
  const amounts = trend.months.map((entry) => amount(entry.amount));
  const maximum = Math.max(...amounts, 1);
  const points = amounts.map((entry, index) => `${index * (100 / Math.max(amounts.length - 1, 1))},${100 - (entry / maximum) * 100}`).join(" ");
  const alternative = trend.months.map((entry) => `${entry.month}: ${entry.amount}`).join("; ");

  return <figure className={styles.chartCard}>
    <figcaption><h2>{l("Six-month spending trend", "Динамика расходов за шесть месяцев")}</h2><p>{l("Monthly expenses ending in", "Расходы по месяцам до")} {trend.endMonth}.</p></figcaption>
    <svg aria-label={`${l("Six-month spending trend.", "Динамика расходов за шесть месяцев.")} ${alternative}`} className={styles.lineChart} preserveAspectRatio="none" role="img" viewBox="0 0 100 100">
      <line stroke="currentColor" strokeOpacity="0.2" strokeWidth="1" x1="0" x2="100" y1="100" y2="100" />
      <polyline fill="none" points={points} stroke="var(--color-primary)" strokeWidth="3" vectorEffect="non-scaling-stroke" />
    </svg>
    <ul className={styles.trendValues}>{trend.months.map((entry) => <li key={entry.month}><span>{entry.month}</span><strong>{entry.amount}</strong></li>)}</ul>
  </figure>;
}

export function DashboardManager() {
  const { l } = useLocalization();
  const [selectedMonth, setSelectedMonth] = useState(currentMonth);
  const dashboardQuery = useQuery({ queryKey: ["dashboard", selectedMonth], queryFn: ({ signal }) => getDashboard(selectedMonth, signal) });
  const trendQuery = useQuery({ queryKey: ["dashboard", "spending-trend", selectedMonth], queryFn: ({ signal }) => getSpendingTrend(selectedMonth, signal) });
  const isLoading = dashboardQuery.isPending || trendQuery.isPending;
  const isError = dashboardQuery.isError || trendQuery.isError;
  const dashboard = dashboardQuery.data;
  const trend = trendQuery.data;
  const isEmpty = dashboard !== undefined && trend !== undefined && dashboard.expensesByCategory.length === 0 && dashboard.topExpenseCategories.length === 0 && trend.months.every((entry) => amount(entry.amount) === 0);

  return <section className={styles.page}>
    <header className={styles.header}><div><h1>{l("Dashboard", "Обзор")}</h1><p>{l("Review your monthly expenses and recent spending trend.", "Просматривайте ежемесячные расходы и их динамику.")}</p></div><div className={styles.monthPicker}><label htmlFor="dashboard-month-picker">{l("Month", "Месяц")}</label><input id="dashboard-month-picker" onChange={(event) => { if (event.target.value !== "") setSelectedMonth(event.target.value); }} type="month" value={selectedMonth} /></div></header>
    {isLoading && <p role="status">{l("Loading dashboard…", "Загрузка обзора…")}</p>}
    {isError && <p className={styles.error} role="alert">{l("We could not load the dashboard. Please refresh the page.", "Не удалось загрузить обзор. Обновите страницу.")}</p>}
    {isEmpty && <p className={styles.empty}>{l("No expense data is available for this period.", "За этот период нет данных о расходах.")}</p>}
    {dashboard !== undefined && trend !== undefined && !isEmpty && !isError && <div className={styles.content}>
      {dashboard.expensesByCategory.length > 0 && <PieChart categories={dashboard.expensesByCategory} />}
      <LineChart trend={trend} />
      <section aria-labelledby="top-categories-heading" className={styles.topCategories}><h2 id="top-categories-heading">{l("Top 5 expense categories", "Топ-5 категорий расходов")}</h2>{dashboard.topExpenseCategories.length === 0 ? <p>{l("No expense categories for this month.", "В этом месяце нет категорий расходов.")}</p> : <ol>{dashboard.topExpenseCategories.map((category) => <li key={category.categoryId}><span>{category.categoryIcon} {category.categoryName}</span><strong>{category.amount}</strong></li>)}</ol>}</section>
    </div>}
  </section>;
}
