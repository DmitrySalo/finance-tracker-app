import { RegisterForm } from "../features/auth/components/RegisterForm";
import styles from "./Page.module.css";

export function RegisterPage() {
  return <main className={styles.centered}><section className={styles.card}><h1>Create your account</h1><p>Start tracking your finances in one place.</p><RegisterForm /></section></main>;
}
