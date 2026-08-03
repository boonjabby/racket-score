import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Racket Score",
  description: "Fast, spoken scoring for pickleball and racquet sports.",
  applicationName: "Racket Score",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, title: "Racket Score", statusBarStyle: "black-translucent" },
  icons: { icon: "/favicon.svg", apple: "/favicon.svg" },
};

export const viewport: Viewport = { themeColor: "#102e28", width: "device-width", initialScale: 1, viewportFit: "cover" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
