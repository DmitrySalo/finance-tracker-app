import { Link } from "react-router";
import styles from "./Page.module.css";
import { useLocalization } from "../shared/localization/LocalizationProvider";

interface PublicPageProps {
  title: string;
}

export function PublicPage({ title }: PublicPageProps) {
  const { l } = useLocalization();
  const isLogin = title === "Sign in";
  const message = isLogin
    ? l("Authentication will be available in the next step.", "Аутентификация будет доступна на следующем этапе.")
    : l("Registration will be available in the next step.", "Регистрация будет доступна на следующем этапе.");

  return (
    <main className={styles.centered}>
      <section className={styles.card}>
        <h1>{title}</h1>
        <p>{message}</p>
        <Link to={isLogin ? "/register" : "/login"}>{isLogin ? l("Create an account", "Создать аккаунт") : l("Sign in", "Войти")}</Link>
      </section>
    </main>
  );
}
