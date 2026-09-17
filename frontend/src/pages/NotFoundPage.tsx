import { Link } from "react-router";
import styles from "./Page.module.css";
import { useLocalization } from "../shared/localization/LocalizationProvider";

export function NotFoundPage() {
  const { t } = useLocalization();
  return (
    <main className={styles.centered}>
      <section className={styles.card}>
        <h1>{t("notFound.title")}</h1>
        <Link to="/login">{t("notFound.login")}</Link>
      </section>
    </main>
  );
}
