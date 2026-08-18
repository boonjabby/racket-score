import type { Metadata } from "next";
import Link from "next/link";
import styles from "./privacy.module.css";

export const metadata: Metadata = {
  title: "Privacy Policy · Racket Score",
  description: "How Racket Score handles local scores and optional live match sharing.",
};

export default function PrivacyPolicy() {
  return <main className={styles.page}>
    <article>
      <Link className={styles.back} href="/">← Racket Score</Link>
      <p className={styles.kicker}>PRIVACY POLICY</p>
      <h1>Simple scoring. Minimal data.</h1>
      <p className={styles.updated}>Effective 18 August 2026</p>

      <section>
        <h2>What stays on your device</h2>
        <p>Scores, match history, player labels and court preferences are normally stored only on your phone, watch or browser. Racket Score does not upload this local history. You can remove individual matches, clear the app’s storage, or uninstall the app to delete it.</p>
      </section>

      <section>
        <h2>Optional live sharing</h2>
        <p>Live sharing is off until you choose to start it. When enabled, Racket Score sends the sport, score, server position, match state and update time to Supabase so spectators with the random match code can follow the scoreboard. It does not send your contacts, precise location, photos, microphone recordings or local match history.</p>
        <p>The shared scoreboard is deleted when you stop sharing or automatically after its eight-hour expiry period. Expired records are removed by an hourly cleanup process.</p>
      </section>

      <section>
        <h2>Service information</h2>
        <p>Supabase processes the optional live scoreboard and an anonymous session identifier. Like most internet services, infrastructure providers may process technical information such as an IP address and request logs for security and reliability. Spectators can see a shared scoreboard only when they possess its match code or link.</p>
      </section>

      <section>
        <h2>Sharing, advertising and sales</h2>
        <p>Racket Score does not currently contain advertising, analytics trackers or data-selling features. Scoreboard information is not sold. If these practices change, this policy and the app’s disclosures will be updated before the change is released.</p>
      </section>

      <section>
        <h2>Security and choices</h2>
        <p>Cloud traffic uses encrypted HTTPS connections. Database access rules restrict changes to the anonymous device session that created the match. Anyone given a live-match link can view that temporary scoreboard, so share it only with people you intend to invite.</p>
      </section>

      <section>
        <h2>Privacy enquiries</h2>
        <p>Racket Score is currently operated under the developer identity <strong>boonjabby</strong>. Until a dedicated business support address is established, privacy questions or deletion enquiries can be submitted through the project’s <a href="https://github.com/boonjabby/racket-score/issues">public support page</a>. Do not include sensitive personal information in a public request.</p>
      </section>
    </article>
  </main>;
}
