import { NavLink, Outlet, useNavigate } from "react-router";
import { setAccessToken } from "../shared/api/accessToken";
import styles from "./AppShell.module.css";
import { LocaleSwitcher } from "../shared/localization/LocaleSwitcher";
import { useLocalization } from "../shared/localization/LocalizationProvider";

const navigation = [
  ["/dashboard", "nav.dashboard"], ["/transactions", "nav.transactions"], ["/categories", "nav.categories"], ["/budgets", "nav.budgets"], ["/recurring-transactions", "nav.recurring"], ["/audit-logs", "nav.audit"],
] as const;

export function AppShell() {
  const navigate = useNavigate();
  const { t } = useLocalization();

  function logout() {
    setAccessToken(null);
    navigate("/login", { replace: true });
  }

  return (
    <div className={styles.shell}>
      <a className={styles.skipLink} href="#main-content">
        {t("nav.skip")}
      </a>
      <header className={styles.header}>
        <NavLink className={styles.brand} to="/dashboard">
          {t("app.name")}
        </NavLink>
        <nav aria-label={t("nav.primary")} className={styles.navigation}>
          {navigation.map(([to, label]) => (
            <NavLink className={({ isActive }) => (isActive ? styles.activeLink : styles.link)} key={to} to={to}>
               {t(label)}
            </NavLink>
          ))}
        </nav>
        <div className={styles.headerActions}><LocaleSwitcher /><button className={styles.logoutButton} onClick={logout} type="button">{t("auth.signOut")}</button></div>
      </header>
      <main className={styles.main} id="main-content">
        <Outlet />
      </main>
    </div>
  );
}
