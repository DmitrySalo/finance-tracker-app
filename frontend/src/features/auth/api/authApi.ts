import { apiRequest, apiRequestVoid } from "../../../shared/api/client";
import type { CurrentUser, LoginResponse } from "../../../shared/api/models";

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegistrationDetails extends LoginCredentials {
  displayName: string;
  baseCurrency: string;
}

export function login(credentials: LoginCredentials): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/auth/login", {
    clearSessionOnUnauthorized: false,
    method: "POST",
    body: JSON.stringify(credentials),
    headers: { "Content-Type": "application/json" },
  });
}

export function register(details: RegistrationDetails): Promise<void> {
  return apiRequestVoid("/auth/register", {
    clearSessionOnUnauthorized: false,
    method: "POST",
    body: JSON.stringify(details),
    headers: { "Content-Type": "application/json" },
  });
}

export function getCurrentUser(signal?: AbortSignal): Promise<CurrentUser> {
  return apiRequest<CurrentUser>("/auth/me", { signal });
}
