const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const PUBLIC_API_PATHS = new Set([
  "/api/auth/csrf",
  "/api/auth/login",
  "/api/auth/logout",
]);

type CsrfTokenResponse = {
  token: string;
  headerName: string;
};

function readCookie(name: string) {
  if (typeof document === "undefined") {
    return null;
  }

  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie
    .split("; ")
    .find((entry) => entry.startsWith(prefix));

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

export async function refreshCsrfToken() {
  const response = await fetch("/api/auth/csrf", {
    method: "GET",
    credentials: "include",
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error("Unable to obtain a CSRF token.");
  }

  await response.json() as CsrfTokenResponse;

  const token = readCookie(CSRF_COOKIE_NAME);
  if (!token) {
    throw new Error("Unable to read the CSRF token cookie.");
  }

  return token;
}

async function getCsrfToken() {
  return readCookie(CSRF_COOKIE_NAME) ?? refreshCsrfToken();
}

function isPublicApiRequest(input: RequestInfo | URL) {
  if (typeof window === "undefined") return false;
  const rawUrl = input instanceof Request ? input.url : input.toString();
  const path = new URL(rawUrl, window.location.origin).pathname;
  return PUBLIC_API_PATHS.has(path) || path.startsWith("/api/guest/");
}

export async function apiFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
) {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (!SAFE_METHODS.has(method)) {
    headers.set(CSRF_HEADER_NAME, await getCsrfToken());
  }

  const response = await fetch(input, {
    ...init,
    method,
    headers,
    credentials: "include",
  });

  if (response.status === 401 && !isPublicApiRequest(input) && typeof window !== "undefined") {
    window.location.replace("/login");
  }

  return response;
}

export async function getApiErrorMessage(response: Response, fallback: string) {
  const body: { message?: unknown; detail?: unknown } = await response.json().catch(() => ({}));
  if (typeof body.message === "string" && body.message.trim()) return body.message;
  if (typeof body.detail === "string" && body.detail.trim()) return body.detail;
  return fallback;
}
