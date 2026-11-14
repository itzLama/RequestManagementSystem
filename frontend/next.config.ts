import type { NextConfig } from "next";

// Security headers for every page served by Next.js. API responses (/api/*) are proxied to Spring
// Boot, which sets its own headers, so they are excluded here to avoid duplicates. A full
// Content-Security-Policy is a planned follow-up; for now only framing is restricted.
// HSTS belongs on the HTTPS terminator once one exists.
const securityHeaders = [
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "same-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
];

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async headers() {
    return [{ source: "/((?!api/).*)", headers: securityHeaders }];
  },
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
