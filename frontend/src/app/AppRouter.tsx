import { useSyncExternalStore } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { hasAccessToken, subscribeToAccessToken } from "../shared/api/accessToken";
import { LoginPage } from "../pages/LoginPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { ProtectedPage } from "../pages/ProtectedPage";
import { RegisterPage } from "../pages/RegisterPage";
import { CategoriesManager } from "../features/categories/components/CategoriesManager";
import { BudgetsManager } from "../features/budgets/components/BudgetsManager";
import { DashboardManager } from "../features/dashboard/components/DashboardManager";
import { TransactionsManager } from "../features/transactions/components/TransactionsManager";
import { RecurringTransactionsManager } from "../features/recurring-transactions/components/RecurringTransactionsManager";
import { AuditLogManager } from "../features/audit/components/AuditLogManager";
import { AppShell } from "./AppShell";

const protectedRoutes = ["dashboard", "transactions", "categories", "budgets", "recurring-transactions", "audit-logs"] as const;

function ProtectedRoute() {
  const isAuthenticated = useSyncExternalStore(subscribeToAccessToken, hasAccessToken, hasAccessToken);
  return isAuthenticated ? <AppShell /> : <Navigate replace to="/login" />;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Navigate replace to="/dashboard" />} path="/" />
        <Route element={<LoginPage />} path="/login" />
        <Route element={<RegisterPage />} path="/register" />
        <Route element={<ProtectedRoute />}>
          {protectedRoutes.map((path) => (
            <Route element={path === "dashboard" ? <DashboardManager /> : path === "categories" ? <CategoriesManager /> : path === "transactions" ? <TransactionsManager /> : path === "budgets" ? <BudgetsManager /> : path === "recurring-transactions" ? <RecurringTransactionsManager /> : path === "audit-logs" ? <AuditLogManager /> : <ProtectedPage title="" />} key={path} path={path} />
          ))}
        </Route>
        <Route element={<NotFoundPage />} path="*" />
      </Routes>
    </BrowserRouter>
  );
}
