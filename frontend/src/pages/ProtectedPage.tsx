import styles from "./Page.module.css";

interface ProtectedPageProps {
  title: string;
}

export function ProtectedPage({ title }: ProtectedPageProps) {
  return (
    <section className={styles.card}>
      <h1>{title}</h1>
      <p>This section is ready for its feature implementation.</p>
    </section>
  );
}
