import { Link } from "react-router";
import styles from "./Page.module.css";

export function NotFoundPage() {
  return (
    <main className={styles.centered}>
      <section className={styles.card}>
        <h1>Page not found</h1>
        <Link to="/login">Go to sign in</Link>
      </section>
    </main>
  );
}
