import type { Metadata, Viewport } from "next";
import "./globals.css";

const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";

export const metadata: Metadata = {
  title: "Racket Score",
  description: "Fast, spoken scoring for pickleball and racquet sports.",
  applicationName: "Racket Score",
  manifest: `${basePath}/manifest.webmanifest`,
  appleWebApp: { capable: true, title: "Racket Score", statusBarStyle: "black-translucent" },
  icons: {
    icon: [{ url: `${basePath}/icon-192.png`, sizes: "192x192", type: "image/png" }, { url: `${basePath}/favicon.svg`, type: "image/svg+xml" }],
    apple: `${basePath}/apple-touch-icon.png`,
  },
};

export const viewport: Viewport = { themeColor: "#102e28", width: "device-width", initialScale: 1, viewportFit: "cover" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
