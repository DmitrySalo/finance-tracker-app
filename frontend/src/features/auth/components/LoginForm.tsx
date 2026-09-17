import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { z } from "zod";
import { setAccessToken } from "../../../shared/api/accessToken";
import { ApiClientError } from "../../../shared/api/client";
import { login } from "../api/authApi";
import styles from "./AuthForm.module.css";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";

function loginSchema(t: ReturnType<typeof useLocalization>["t"]) { return z.object({
  email: z.string().trim().min(1, t("validation.emailRequired")).email(t("validation.email")).max(254),
  password: z.string().min(1, t("validation.passwordRequired")).refine(
    (password) => new TextEncoder().encode(password).length <= 72,
    t("validation.passwordBytes"),
  ),
}); }

type LoginFormValues = z.infer<ReturnType<typeof loginSchema>>;

function formError(error: unknown, t: ReturnType<typeof useLocalization>["t"]): string {
  if (error instanceof ApiClientError && error.status === 401) {
    return t("auth.invalid");
  }
  if (error instanceof ApiClientError && error.status === 429) {
    return t("auth.rateLimited");
  }
  return t("auth.loginFailed");
}

export function LoginForm() {
  const navigate = useNavigate();
  const { t } = useLocalization();
  const { formState: { errors, isSubmitting }, handleSubmit, register } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema(t)),
  });
  const loginMutation = useMutation({ mutationFn: login });

  async function onSubmit(values: LoginFormValues) {
    try {
      const response = await loginMutation.mutateAsync(values);
      setAccessToken(response.accessToken);
      navigate("/dashboard", { replace: true });
    } catch {
      // The mutation state renders a safe error message and retains form values.
    }
  }

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit(onSubmit)}>
      <label className={styles.field}>
        {t("auth.email")}
        <input aria-describedby={errors.email ? "login-email-error" : undefined} aria-invalid={Boolean(errors.email)} autoComplete="email" type="email" {...register("email")} />
        {errors.email && <p className={styles.fieldError} id="login-email-error" role="alert">{errors.email.message}</p>}
      </label>
      <label className={styles.field}>
        {t("auth.password")}
        <input aria-describedby={errors.password ? "login-password-error" : undefined} aria-invalid={Boolean(errors.password)} autoComplete="current-password" type="password" {...register("password")} />
        {errors.password && <p className={styles.fieldError} id="login-password-error" role="alert">{errors.password.message}</p>}
      </label>
      {loginMutation.isError && <p className={styles.formError} role="alert">{formError(loginMutation.error, t)}</p>}
      <button className={styles.submitButton} disabled={isSubmitting} type="submit">{isSubmitting ? t("auth.signingIn") : t("auth.signIn")}</button>
      <p className={styles.footer}>{t("auth.new")} <Link to="/register">{t("auth.createAccount")}</Link></p>
    </form>
  );
}
