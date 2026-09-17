import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";

export type Locale = "en" | "ru";

const storageKey = "finance-tracker.locale";

const messages = {
  en: {
    "language.label": "Language", "language.en": "English", "language.ru": "Русский",
    "app.name": "Finance Tracker", "nav.dashboard": "Dashboard", "nav.transactions": "Transactions", "nav.categories": "Categories", "nav.budgets": "Budgets", "nav.recurring": "Recurring", "nav.audit": "Audit log", "nav.primary": "Primary navigation", "nav.skip": "Skip to content", "auth.signIn": "Sign in", "auth.signOut": "Sign out", "auth.createAccount": "Create account", "auth.loginIntro": "Sign in to continue managing your finances.", "auth.registerTitle": "Create your account", "auth.registerIntro": "Start tracking your finances in one place.", "auth.email": "Email", "auth.password": "Password", "auth.name": "Name", "auth.currency": "Base currency", "auth.signingIn": "Signing in…", "auth.creating": "Creating account…", "auth.new": "New to Finance Tracker?", "auth.existing": "Already have an account?", "auth.created": "Your account has been created. You can now sign in.", "error.title": "Something went wrong", "error.refresh": "Please refresh the page and try again.", "notFound.title": "Page not found", "notFound.login": "Go to sign in", "notification.dismiss": "Dismiss notification", "dismiss": "Dismiss",
    "validation.emailRequired": "Enter your email address.", "validation.email": "Enter a valid email address.", "validation.passwordRequired": "Enter your password.", "validation.passwordLength": "Use at least 12 characters.", "validation.passwordBytes": "Use no more than 72 bytes.", "validation.name": "Enter your name.", "validation.currency": "Enter a three-letter currency code.", "auth.invalid": "Email or password is incorrect.", "auth.rateLimited": "Too many attempts. Please try again later.", "auth.loginFailed": "We could not sign you in. Please try again.", "auth.registerFailed": "We could not create your account. Please try again.",
  },
  ru: {
    "language.label": "Язык", "language.en": "English", "language.ru": "Русский",
    "app.name": "Финансовый трекер", "nav.dashboard": "Обзор", "nav.transactions": "Операции", "nav.categories": "Категории", "nav.budgets": "Бюджеты", "nav.recurring": "Регулярные", "nav.audit": "Журнал аудита", "nav.primary": "Основная навигация", "nav.skip": "Перейти к содержимому", "auth.signIn": "Войти", "auth.signOut": "Выйти", "auth.createAccount": "Создать аккаунт", "auth.loginIntro": "Войдите, чтобы продолжить управление финансами.", "auth.registerTitle": "Создайте аккаунт", "auth.registerIntro": "Начните учитывать финансы в одном месте.", "auth.email": "Электронная почта", "auth.password": "Пароль", "auth.name": "Имя", "auth.currency": "Основная валюта", "auth.signingIn": "Вход…", "auth.creating": "Создание аккаунта…", "auth.new": "Впервые в Финансовом трекере?", "auth.existing": "Уже есть аккаунт?", "auth.created": "Аккаунт создан. Теперь вы можете войти.", "error.title": "Что-то пошло не так", "error.refresh": "Обновите страницу и попробуйте снова.", "notFound.title": "Страница не найдена", "notFound.login": "Перейти ко входу", "notification.dismiss": "Закрыть уведомление", "dismiss": "Закрыть",
    "validation.emailRequired": "Введите адрес электронной почты.", "validation.email": "Введите корректный адрес электронной почты.", "validation.passwordRequired": "Введите пароль.", "validation.passwordLength": "Используйте не менее 12 символов.", "validation.passwordBytes": "Используйте не более 72 байт.", "validation.name": "Введите имя.", "validation.currency": "Введите трёхбуквенный код валюты.", "auth.invalid": "Неверный адрес электронной почты или пароль.", "auth.rateLimited": "Слишком много попыток. Повторите позднее.", "auth.loginFailed": "Не удалось войти. Попробуйте снова.", "auth.registerFailed": "Не удалось создать аккаунт. Попробуйте снова.",
  },
} as const;

type MessageKey = keyof typeof messages.en;
interface LocalizationContextValue { locale: Locale; setLocale: (locale: Locale) => void; t: (key: MessageKey) => string; l: (english: string, russian: string) => string; }
const missingProvider: LocalizationContextValue = { locale: "en", setLocale: () => undefined, t: (key) => messages.en[key], l: (english) => english };
export const LocalizationContext = createContext<LocalizationContextValue>(missingProvider);

function initialLocale(): Locale {
  try { return localStorage.getItem(storageKey) === "en" ? "en" : "ru"; } catch { return "ru"; }
}

export function LocalizationProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<Locale>(initialLocale);
  useEffect(() => { document.documentElement.lang = locale; try { localStorage.setItem(storageKey, locale); } catch { /* Locale remains available for this session. */ } }, [locale]);
  return <LocalizationContext.Provider value={{ locale, setLocale, t: (key) => messages[locale][key], l: (english, russian) => locale === "ru" ? russian : english }}>{children}</LocalizationContext.Provider>;
}

export function useLocalization(): LocalizationContextValue {
  return useContext(LocalizationContext);
}
