import { RegisterForm } from "../features/auth/components/RegisterForm";
import styles from "./Page.module.css";
import { LocaleSwitcher } from "../shared/localization/LocaleSwitcher";
import { useLocalization } from "../shared/localization/LocalizationProvider";

export function RegisterPage() {
  const { t } = useLocalization();
  return <main className={styles.centered}><section className={styles.card}><LocaleSwitcher /><h1>{t("auth.registerTitle")}</h1><p>{t("auth.registerIntro")}</p><RegisterForm /></section></main>;
}
