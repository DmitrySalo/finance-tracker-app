import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { hasAccessToken } from "../shared/api/accessToken";
import { NotFoundPage } from "../pages/NotFoundPage";
import { ProtectedPage } from "../pages/ProtectedPage";
import { PublicPage } from "../pages/PublicPage";
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
  return hasAccessToken() ? <AppShell /> : <Navigate replace to="/login" />;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Navigate replace to="/dashboard" />} path="/" />
        <Route element={<PublicPage title="Sign in" />} path="/login" />
        <Route element={<PublicPage title="Create your account" />} path="/register" />
        <Route element={<ProtectedRoute />}>
          {protectedRoutes.map(([path, title]) => (
            <Route element={<ProtectedPage title={title} />} key={path} path={path} />
          ))}
        </Route>
        <Route element={<NotFoundPage />} path="*" />
      </Routes>
    </BrowserRouter>
  );
}
