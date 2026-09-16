import { NavLink, Outlet } from "react-router";
import styles from "./AppShell.module.css";

const navigation = [
  ["/dashboard", "Dashboard"],
  ["/transactions", "Transactions"],
  ["/categories", "Categories"],
  ["/budgets", "Budgets"],
  ["/recurring-transactions", "Recurring"],
  ["/audit-logs", "Audit log"],
] as const;

export function AppShell() {
  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main-content">
        Skip to content
      </a>
      <header className={styles.header}>
        <NavLink className={styles.brand} to="/dashboard">
          Finance Tracker
        </NavLink>
        <nav aria-label="Primary navigation" className={styles.navigation}>
          {navigation.map(([to, label]) => (
            <NavLink className={({ isActive }) => (isActive ? styles.activeLink : styles.link)} key={to} to={to}>
              {label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className={styles.main} id="main-content">
        <Outlet />
      </main>
    </div>
  );
}
