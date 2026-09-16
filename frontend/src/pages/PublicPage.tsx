import { Link } from "react-router";
import styles from "./Page.module.css";

interface PublicPageProps {
  title: string;
}

export function PublicPage({ title }: PublicPageProps) {
  const isLogin = title === "Sign in";
  const message = isLogin
    ? "Authentication will be available in the next step."
    : "Registration will be available in the next step.";

  return (
    <main className={styles.centered}>
      <section className={styles.card}>
        <h1>{title}</h1>
        <p>{message}</p>
        <Link to={isLogin ? "/register" : "/login"}>{isLogin ? "Create an account" : "Sign in"}</Link>
      </section>
    </main>
  );
}
