import styles from "./Page.module.css";
import { useLocalization } from "../shared/localization/LocalizationProvider";

interface ProtectedPageProps {
  title: string;
}

export function ProtectedPage({ title }: ProtectedPageProps) {
  const { l } = useLocalization();
  return (
    <section className={styles.card}>
      <h1>{title}</h1>
      <p>{l("This section is ready for its feature implementation.", "Этот раздел готов к реализации функции.")}</p>
    </section>
  );
}
