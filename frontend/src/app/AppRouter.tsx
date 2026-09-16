import { useSyncExternalStore } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { hasAccessToken, subscribeToAccessToken } from "../shared/api/accessToken";
import { LoginPage } from "../pages/LoginPage";
import { NotFoundPage } from "../pages/NotFoundPage";
import { ProtectedPage } from "../pages/ProtectedPage";
import { RegisterPage } from "../pages/RegisterPage";
import { CategoriesManager } from "../features/categories/components/CategoriesManager";
import { BudgetsManager } from "../features/budgets/components/BudgetsManager";
import { TransactionsManager } from "../features/transactions/components/TransactionsManager";
import { AppShell } from "./AppShell";

const protectedRoutes = [
  ["dashboard", "Dashboard"],
  ["transactions", "Transactions"],
  ["categories", "Categories"],
  ["budgets", "Budgets"],
  ["recurring-transactions", "Recurring transactions"],
  ["audit-logs", "Audit log"],
] as const;

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
          {protectedRoutes.map(([path, title]) => (
            <Route element={path === "categories" ? <CategoriesManager /> : path === "transactions" ? <TransactionsManager /> : path === "budgets" ? <BudgetsManager /> : <ProtectedPage title={title} />} key={path} path={path} />
          ))}
        </Route>
        <Route element={<NotFoundPage />} path="*" />
      </Routes>
    </BrowserRouter>
  );
}
