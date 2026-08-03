# Racket Score

A touch-first Progressive Web App for scoring pickleball and other racquet sports. The top court is the opponent; the bottom court is your side. Tap the side that won the rally.

## Included

- Pickleball doubles with opening `0–0–2`, first/second server rotation, side-out scoring, and service-court indication
- Pickleball singles, tennis/padel, badminton, table tennis, squash, and racquetball
- Spoken scores from the serving side's perspective
- Configurable first serving side and pickleball server number
- Player (top/bottom) and umpire (left/right) court views
- Monochrome circular watch preview
- Center-net undo and new-game controls
- Local preference storage, vibration, winner trophy, and confetti
- Installable manifest and offline service worker

## Run locally

Requirements: Node.js 22.13 or newer and pnpm.

```bash
pnpm install
pnpm dev
```

Open the local address shown in the terminal. To create a production build:

```bash
pnpm build
```

## Project structure

```text
app/
  page.tsx       Touch interface, setup, speech, history, and presentation modes
  scoring.ts     Pure scoring engine and server-perspective announcements
  globals.css    Responsive phone, umpire, and watch-preview styling
  layout.tsx     PWA metadata and mobile viewport
public/
  manifest.webmanifest
  sw.js
  favicon.svg
```

The scoring engine has no browser or React dependencies. That boundary is intentional: its state transitions can be ported to Kotlin for a Samsung Wear OS app, while the screen and speech/vibration adapters are replaced with Compose and Wear OS equivalents.

## Install on Android

Deploy the app over HTTPS, open it in Chrome or Samsung Internet, then choose **Install app** or **Add to Home screen**. Speech and vibration availability depend on the browser and device settings.
