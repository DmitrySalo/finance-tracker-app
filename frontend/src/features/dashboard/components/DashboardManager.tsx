import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { DashboardCategoryExpense, SpendingTrend } from "../../../shared/api/models";
import { getDashboard, getSpendingTrend } from "../api/dashboardApi";
import styles from "./DashboardManager.module.css";

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
  const total = categories.reduce((sum, category) => sum + amount(category.amount), 0);
  const alternative = categories.map((category) => `${category.categoryName}: ${category.amount}`).join("; ");

  return <figure className={styles.chartCard}>
    <figcaption><h2>Expenses by category</h2><p>Monthly expense breakdown.</p></figcaption>
    <svg aria-label={`Expenses by category. ${alternative}`} className={styles.pieChart} role="img" viewBox="0 0 100 100">
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
  const amounts = trend.months.map((entry) => amount(entry.amount));
  const maximum = Math.max(...amounts, 1);
  const points = amounts.map((entry, index) => `${index * (100 / Math.max(amounts.length - 1, 1))},${100 - (entry / maximum) * 100}`).join(" ");
  const alternative = trend.months.map((entry) => `${entry.month}: ${entry.amount}`).join("; ");

  return <figure className={styles.chartCard}>
    <figcaption><h2>Six-month spending trend</h2><p>Monthly expenses ending in {trend.endMonth}.</p></figcaption>
    <svg aria-label={`Six-month spending trend. ${alternative}`} className={styles.lineChart} preserveAspectRatio="none" role="img" viewBox="0 0 100 100">
      <line stroke="currentColor" strokeOpacity="0.2" strokeWidth="1" x1="0" x2="100" y1="100" y2="100" />
      <polyline fill="none" points={points} stroke="var(--color-primary)" strokeWidth="3" vectorEffect="non-scaling-stroke" />
    </svg>
    <ul className={styles.trendValues}>{trend.months.map((entry) => <li key={entry.month}><span>{entry.month}</span><strong>{entry.amount}</strong></li>)}</ul>
  </figure>;
}

export function DashboardManager() {
  const [selectedMonth, setSelectedMonth] = useState(currentMonth);
  const dashboardQuery = useQuery({ queryKey: ["dashboard", selectedMonth], queryFn: ({ signal }) => getDashboard(selectedMonth, signal) });
  const trendQuery = useQuery({ queryKey: ["dashboard", "spending-trend", selectedMonth], queryFn: ({ signal }) => getSpendingTrend(selectedMonth, signal) });
  const isLoading = dashboardQuery.isPending || trendQuery.isPending;
  const isError = dashboardQuery.isError || trendQuery.isError;
  const dashboard = dashboardQuery.data;
  const trend = trendQuery.data;
  const isEmpty = dashboard !== undefined && trend !== undefined && dashboard.expensesByCategory.length === 0 && dashboard.topExpenseCategories.length === 0 && trend.months.every((entry) => amount(entry.amount) === 0);

  return <section className={styles.page}>
    <header className={styles.header}><div><h1>Dashboard</h1><p>Review your monthly expenses and recent spending trend.</p></div><div className={styles.monthPicker}><label htmlFor="dashboard-month-picker">Month</label><input id="dashboard-month-picker" onChange={(event) => { if (event.target.value !== "") setSelectedMonth(event.target.value); }} type="month" value={selectedMonth} /></div></header>
    {isLoading && <p role="status">Loading dashboard…</p>}
    {isError && <p className={styles.error} role="alert">We could not load the dashboard. Please refresh the page.</p>}
    {isEmpty && <p className={styles.empty}>No expense data is available for this period.</p>}
    {dashboard !== undefined && trend !== undefined && !isEmpty && !isError && <div className={styles.content}>
      {dashboard.expensesByCategory.length > 0 && <PieChart categories={dashboard.expensesByCategory} />}
      <LineChart trend={trend} />
      <section aria-labelledby="top-categories-heading" className={styles.topCategories}><h2 id="top-categories-heading">Top 5 expense categories</h2>{dashboard.topExpenseCategories.length === 0 ? <p>No expense categories for this month.</p> : <ol>{dashboard.topExpenseCategories.map((category) => <li key={category.categoryId}><span>{category.categoryIcon} {category.categoryName}</span><strong>{category.amount}</strong></li>)}</ol>}</section>
    </div>}
  </section>;
}
