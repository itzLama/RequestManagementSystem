const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

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

export async function apiFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
) {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (!SAFE_METHODS.has(method)) {
    headers.set(CSRF_HEADER_NAME, await getCsrfToken());
  }

  return fetch(input, {
    ...init,
    method,
    headers,
    credentials: "include",
  });
}
