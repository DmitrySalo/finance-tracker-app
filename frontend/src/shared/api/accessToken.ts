let accessToken: string | null = null;
const listeners = new Set<() => void>();

export function setAccessToken(token: string | null): void {
  if (accessToken === token) {
    return;
  }

  accessToken = token;
  listeners.forEach((listener) => listener());
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function hasAccessToken(): boolean {
  return accessToken !== null;
}

export function subscribeToAccessToken(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
