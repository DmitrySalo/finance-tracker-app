import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { z } from "zod";
import { ApiClientError } from "../../../shared/api/client";
import { useNotifications } from "../../../shared/notifications/useNotifications";
import { register as registerUser } from "../api/authApi";
import styles from "./AuthForm.module.css";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";

function registerSchema(t: ReturnType<typeof useLocalization>["t"]) { return z.object({
  displayName: z.string().trim().min(1, t("validation.name")).max(100),
  email: z.string().trim().min(1, t("validation.emailRequired")).email(t("validation.email")).max(254),
  password: z.string().min(12, t("validation.passwordLength")).refine(
    (password) => new TextEncoder().encode(password).length <= 72,
    t("validation.passwordBytes"),
  ),
  baseCurrency: z.string().trim().regex(/^[A-Z]{3}$/, t("validation.currency")),
}); }

type RegisterFormValues = z.infer<ReturnType<typeof registerSchema>>;

function formError(error: unknown, t: ReturnType<typeof useLocalization>["t"]): string {
  if (error instanceof ApiClientError && error.status === 429) {
    return t("auth.rateLimited");
  }
  return t("auth.registerFailed");
}

export function RegisterForm() {
  const navigate = useNavigate();
  const { t } = useLocalization();
  const { notify } = useNotifications();
  const { formState: { errors, isSubmitting }, handleSubmit, register } = useForm<RegisterFormValues>({
    defaultValues: { baseCurrency: "USD" },
    resolver: zodResolver(registerSchema(t)),
  });
  const registrationMutation = useMutation({ mutationFn: registerUser });

  async function onSubmit(values: RegisterFormValues) {
    try {
      await registrationMutation.mutateAsync(values);
       notify(t("auth.created"));
      navigate("/login", { replace: true });
    } catch {
      // The mutation state renders a safe error message and retains form values.
    }
  }

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit(onSubmit)}>
      <label className={styles.field}>{t("auth.name")}
        <input aria-describedby={errors.displayName ? "register-name-error" : undefined} aria-invalid={Boolean(errors.displayName)} autoComplete="name" {...register("displayName")} />
        {errors.displayName && <p className={styles.fieldError} id="register-name-error" role="alert">{errors.displayName.message}</p>}
      </label>
      <label className={styles.field}>{t("auth.email")}
        <input aria-describedby={errors.email ? "register-email-error" : undefined} aria-invalid={Boolean(errors.email)} autoComplete="email" type="email" {...register("email")} />
        {errors.email && <p className={styles.fieldError} id="register-email-error" role="alert">{errors.email.message}</p>}
      </label>
      <label className={styles.field}>{t("auth.password")}
        <input aria-describedby={errors.password ? "register-password-error" : undefined} aria-invalid={Boolean(errors.password)} autoComplete="new-password" type="password" {...register("password")} />
        {errors.password && <p className={styles.fieldError} id="register-password-error" role="alert">{errors.password.message}</p>}
      </label>
      <label className={styles.field}>{t("auth.currency")}
        <input aria-describedby={errors.baseCurrency ? "register-currency-error" : undefined} aria-invalid={Boolean(errors.baseCurrency)} autoCapitalize="characters" maxLength={3} placeholder="USD" {...register("baseCurrency")} />
        {errors.baseCurrency && <p className={styles.fieldError} id="register-currency-error" role="alert">{errors.baseCurrency.message}</p>}
      </label>
      {registrationMutation.isError && <p className={styles.formError} role="alert">{formError(registrationMutation.error, t)}</p>}
      <button className={styles.submitButton} disabled={isSubmitting} type="submit">{isSubmitting ? t("auth.creating") : t("auth.createAccount")}</button>
      <p className={styles.footer}>{t("auth.existing")} <Link to="/login">{t("auth.signIn")}</Link></p>
    </form>
  );
}
