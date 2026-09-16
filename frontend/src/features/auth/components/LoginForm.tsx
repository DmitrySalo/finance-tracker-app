import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { z } from "zod";
import { setAccessToken } from "../../../shared/api/accessToken";
import { ApiClientError } from "../../../shared/api/client";
import { login } from "../api/authApi";
import styles from "./AuthForm.module.css";

const loginSchema = z.object({
  email: z.string().trim().min(1, "Enter your email address.").email("Enter a valid email address.").max(254),
  password: z.string().min(1, "Enter your password.").refine(
    (password) => new TextEncoder().encode(password).length <= 72,
    "Use no more than 72 bytes.",
  ),
});

type LoginFormValues = z.infer<typeof loginSchema>;

function formError(error: unknown): string {
  if (error instanceof ApiClientError && error.status === 401) {
    return "Email or password is incorrect.";
  }
  if (error instanceof ApiClientError && error.status === 429) {
    return "Too many attempts. Please try again later.";
  }
  return "We could not sign you in. Please try again.";
}

export function LoginForm() {
  const navigate = useNavigate();
  const { formState: { errors, isSubmitting }, handleSubmit, register } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
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
        Email
        <input aria-describedby={errors.email ? "login-email-error" : undefined} aria-invalid={Boolean(errors.email)} autoComplete="email" type="email" {...register("email")} />
        {errors.email && <p className={styles.fieldError} id="login-email-error" role="alert">{errors.email.message}</p>}
      </label>
      <label className={styles.field}>
        Password
        <input aria-describedby={errors.password ? "login-password-error" : undefined} aria-invalid={Boolean(errors.password)} autoComplete="current-password" type="password" {...register("password")} />
        {errors.password && <p className={styles.fieldError} id="login-password-error" role="alert">{errors.password.message}</p>}
      </label>
      {loginMutation.isError && <p className={styles.formError} role="alert">{formError(loginMutation.error)}</p>}
      <button className={styles.submitButton} disabled={isSubmitting} type="submit">{isSubmitting ? "Signing in…" : "Sign in"}</button>
      <p className={styles.footer}>New to Finance Tracker? <Link to="/register">Create an account</Link></p>
    </form>
  );
}
