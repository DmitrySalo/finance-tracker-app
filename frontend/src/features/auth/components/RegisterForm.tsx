import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { z } from "zod";
import { ApiClientError } from "../../../shared/api/client";
import { useNotifications } from "../../../shared/notifications/useNotifications";
import { register as registerUser } from "../api/authApi";
import styles from "./AuthForm.module.css";

const registerSchema = z.object({
  displayName: z.string().trim().min(1, "Enter your name.").max(100),
  email: z.string().trim().min(1, "Enter your email address.").email("Enter a valid email address.").max(254),
  password: z.string().min(12, "Use at least 12 characters.").refine(
    (password) => new TextEncoder().encode(password).length <= 72,
    "Use no more than 72 bytes.",
  ),
  baseCurrency: z.string().trim().regex(/^[A-Z]{3}$/, "Enter a three-letter currency code."),
});

type RegisterFormValues = z.infer<typeof registerSchema>;

function formError(error: unknown): string {
  if (error instanceof ApiClientError && error.status === 429) {
    return "Too many attempts. Please try again later.";
  }
  return "We could not create your account. Please try again.";
}

export function RegisterForm() {
  const navigate = useNavigate();
  const { notify } = useNotifications();
  const { formState: { errors, isSubmitting }, handleSubmit, register } = useForm<RegisterFormValues>({
    defaultValues: { baseCurrency: "USD" },
    resolver: zodResolver(registerSchema),
  });
  const registrationMutation = useMutation({ mutationFn: registerUser });

  async function onSubmit(values: RegisterFormValues) {
    try {
      await registrationMutation.mutateAsync(values);
      notify("Your account has been created. You can now sign in.");
      navigate("/login", { replace: true });
    } catch {
      // The mutation state renders a safe error message and retains form values.
    }
  }

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit(onSubmit)}>
      <label className={styles.field}>Name
        <input aria-describedby={errors.displayName ? "register-name-error" : undefined} aria-invalid={Boolean(errors.displayName)} autoComplete="name" {...register("displayName")} />
        {errors.displayName && <p className={styles.fieldError} id="register-name-error" role="alert">{errors.displayName.message}</p>}
      </label>
      <label className={styles.field}>Email
        <input aria-describedby={errors.email ? "register-email-error" : undefined} aria-invalid={Boolean(errors.email)} autoComplete="email" type="email" {...register("email")} />
        {errors.email && <p className={styles.fieldError} id="register-email-error" role="alert">{errors.email.message}</p>}
      </label>
      <label className={styles.field}>Password
        <input aria-describedby={errors.password ? "register-password-error" : undefined} aria-invalid={Boolean(errors.password)} autoComplete="new-password" type="password" {...register("password")} />
        {errors.password && <p className={styles.fieldError} id="register-password-error" role="alert">{errors.password.message}</p>}
      </label>
      <label className={styles.field}>Base currency
        <input aria-describedby={errors.baseCurrency ? "register-currency-error" : undefined} aria-invalid={Boolean(errors.baseCurrency)} autoCapitalize="characters" maxLength={3} placeholder="USD" {...register("baseCurrency")} />
        {errors.baseCurrency && <p className={styles.fieldError} id="register-currency-error" role="alert">{errors.baseCurrency.message}</p>}
      </label>
      {registrationMutation.isError && <p className={styles.formError} role="alert">{formError(registrationMutation.error)}</p>}
      <button className={styles.submitButton} disabled={isSubmitting} type="submit">{isSubmitting ? "Creating account…" : "Create account"}</button>
      <p className={styles.footer}>Already have an account? <Link to="/login">Sign in</Link></p>
    </form>
  );
}
