import type { Metadata } from "next";
import { Poppins } from "next/font/google";
import { AppLayout } from "@/components/layout/AppLayout";
import { I18nProvider } from "@/lib/i18n";
import "./globals.css";

const poppins = Poppins({
  variable: "--font-poppins",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});

export const metadata: Metadata = {
  title: "Masar",
  description: "Internal Service Request Management System",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" dir="ltr" className={`${poppins.variable} h-full antialiased`}>
      <body className="min-h-full">
        <I18nProvider><AppLayout>{children}</AppLayout></I18nProvider>
      </body>
    </html>
  );
}
