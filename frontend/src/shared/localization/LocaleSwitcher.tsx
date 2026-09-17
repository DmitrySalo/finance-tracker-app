import { useLocalization } from "./LocalizationProvider";

export function LocaleSwitcher() {
  const { locale, setLocale, t } = useLocalization();
  return <label><span className="visually-hidden">{t("language.label")}</span><select aria-label={t("language.label")} onChange={(event) => setLocale(event.target.value === "en" ? "en" : "ru")} value={locale}><option value="ru">{t("language.ru")}</option><option value="en">{t("language.en")}</option></select></label>;
}
