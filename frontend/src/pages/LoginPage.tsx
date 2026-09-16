import { LoginForm } from "../features/auth/components/LoginForm";
import styles from "./Page.module.css";

export function LoginPage() {
  return <main className={styles.centered}><section className={styles.card}><h1>Sign in</h1><p>Sign in to continue managing your finances.</p><LoginForm /></section></main>;
}
