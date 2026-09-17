import { LoginForm } from "../features/auth/components/LoginForm";
import styles from "./Page.module.css";
import { LocaleSwitcher } from "../shared/localization/LocaleSwitcher";
import { useLocalization } from "../shared/localization/LocalizationProvider";

export function LoginPage() {
  const { t } = useLocalization();
  return <main className={styles.centered}><section className={styles.card}><LocaleSwitcher /><h1>{t("auth.signIn")}</h1><p>{t("auth.loginIntro")}</p><LoginForm /></section></main>;
}
