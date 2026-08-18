# Racket Score

A touch-first Progressive Web App for scoring pickleball and other racquet sports. The top court is the opponent; the bottom court is your side. Tap the side that won the rally.

## Live app - Open in Chrome or Safari

- https://boonjabby.github.io/racket-score/
- GitHub Pages: deployed automatically after the repository is published and Pages is configured to use **GitHub Actions**.

## Included

- Pickleball doubles with opening `0–0–2`, first/second server rotation, side-out scoring, and service-court indication
- Pickleball singles, tennis/padel, badminton, table tennis, squash, and racquetball
- Spoken scores from the serving side's perspective
- Configurable first serving side and pickleball server number
- Player (top/bottom) and umpire (left/right) court views
- Monochrome circular watch preview
- Center-net undo and new-game controls
- Local preference storage, vibration, winner trophy, and confetti
- Automatic recovery of an interrupted match on the same device
- Android install prompt with 192px, 512px, maskable, and Apple touch icons
- Safe in-app notification when an offline update is ready
- Installable manifest and offline service worker
- Optional live sharing with expiring spectator codes and automatic cloud cleanup
- Public privacy policy at `privacy.html`

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

Run the scoring tests with `pnpm test`. Create the static GitHub Pages build with `pnpm build:pages`; its output is written to `out/`.

## GitHub Pages

The workflow at `.github/workflows/pages.yml` tests and deploys the app whenever `main` changes. After creating the GitHub repository:

1. Open **Settings → Pages**.
2. Under **Build and deployment**, select **GitHub Actions**.
3. Push to `main`, or run **Test and deploy PWA** from the Actions tab.

The build accounts for the repository-name path, so the manifest, service worker, icons and offline cache continue to work on a project Pages URL.

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

## Native Android and Wear OS apps

The native Android project lives in [`wearos/`](wearos/README.md). It contains the independently tested standalone watch scorer and an early Android phone companion for live scoring, Court Display Mode, and watch match history. The existing PWA remains the released browser app and will later provide spectator and organiser views.

## Install on Android

Deploy the app over HTTPS, open it in Chrome or Samsung Internet, then choose **Install app** or **Add to Home screen**. Speech and vibration availability depend on the browser and device settings.
