import { getAccessToken, setAccessToken } from "./accessToken";
import type { ApiError, ApiErrorCode } from "./models";

const API_BASE_PATH = "/api/v1";
const apiErrorCodes = new Set<ApiErrorCode>([
  "VALIDATION_FAILED",
  "UNAUTHORIZED",
  "FORBIDDEN",
  "NOT_FOUND",
  "CONFLICT",
  "PAYLOAD_TOO_LARGE",
  "RATE_LIMITED",
  "INTERNAL_ERROR",
]);

export class ApiClientError extends Error {
  public constructor(
    public readonly status: number,
    public readonly apiError: ApiError,
  ) {
    super(apiError.message);
    this.name = "ApiClientError";
  }
}

function isApiError(value: unknown): value is ApiError {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const error = value as Record<string, unknown>;
  return (
    typeof error.code === "string" &&
    apiErrorCodes.has(error.code as ApiErrorCode) &&
    typeof error.message === "string" &&
    (typeof error.traceId === "string" || error.traceId === null) &&
    Array.isArray(error.violations)
  );
}

function fallbackError(status: number): ApiError {
  return {
    code: status === 401 ? "UNAUTHORIZED" : "INTERNAL_ERROR",
    message: "We could not complete your request. Please try again.",
    traceId: null,
    violations: [],
  };
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body: unknown = await response.json();
    return isApiError(body) ? body : fallbackError(response.status);
  } catch {
    return fallbackError(response.status);
  }
}

export interface ApiRequestOptions extends Omit<RequestInit, "body" | "headers"> {
  body?: BodyInit | null;
  clearSessionOnUnauthorized?: boolean;
  headers?: HeadersInit;
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { clearSessionOnUnauthorized = true, response, token } = await sendRequest(path, options);

  if (!response.ok) {
    clearExpiredSession(response.status, token, clearSessionOnUnauthorized);
    throw new ApiClientError(response.status, await parseError(response));
  }

  return (await response.json()) as T;
}

export async function apiRequestVoid(path: string, options: ApiRequestOptions = {}): Promise<void> {
  const { clearSessionOnUnauthorized = true, response, token } = await sendRequest(path, options);

  if (!response.ok) {
    clearExpiredSession(response.status, token, clearSessionOnUnauthorized);
    throw new ApiClientError(response.status, await parseError(response));
  }
}

export async function apiRequestBlob(path: string, options: ApiRequestOptions = {}): Promise<Blob> {
  const { clearSessionOnUnauthorized = true, response, token } = await sendRequest(path, options);

  if (!response.ok) {
    clearExpiredSession(response.status, token, clearSessionOnUnauthorized);
    throw new ApiClientError(response.status, await parseError(response));
  }

  return response.blob();
}

function clearExpiredSession(status: number, token: string | null, clearSessionOnUnauthorized: boolean): void {
  if (clearSessionOnUnauthorized && status === 401 && token !== null && getAccessToken() === token) {
    setAccessToken(null);
  }
}

async function sendRequest(
  path: string,
  options: ApiRequestOptions,
): Promise<{ clearSessionOnUnauthorized: boolean | undefined; response: Response; token: string | null }> {
  const { body, clearSessionOnUnauthorized, headers, ...requestOptions } = options;
  const requestHeaders = new Headers(headers);
  const token = getAccessToken();

  if (token !== null) {
    requestHeaders.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_PATH}${path}`, {
    ...requestOptions,
    body,
    headers: requestHeaders,
  });

  return { clearSessionOnUnauthorized, response, token };
}
