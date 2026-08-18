"use client";

import { useCallback, useEffect, useState } from "react";
import "./watch.css";

const SUPABASE_URL = "https://qpvxeucamyaupnxepwdl.supabase.co";
const PUBLISHABLE_KEY = "sb_publishable_tv5LzPDm-aXMz4j_4Ll4dA_TVQGkcDH";
const CODE_PATTERN = /^[A-HJ-NP-Z2-9]{8}$/;

type PublicSnapshot = {
  sport: string;
  meScore: number;
  opponentScore: number;
  server: "ME" | "OPPONENT";
  serverNumber: number;
  serverCourt: "LEFT" | "RIGHT";
  winner: "ME" | "OPPONENT" | null;
};

type LiveMatch = {
  public_code: string;
  sequence: number;
  snapshot: PublicSnapshot;
  status: "live" | "complete";
  updated_at: string;
  expires_at: string;
};

export default function WatchLivePage() {
  const [code, setCode] = useState("");
  const [match, setMatch] = useState<LiveMatch | null>(null);
  const [status, setStatus] = useState<"enter" | "loading" | "watching" | "missing" | "error">("enter");

  const load = useCallback(async (requestedCode: string, quiet = false) => {
    const clean = requestedCode.trim().toUpperCase();
    if (!CODE_PATTERN.test(clean)) { setStatus("missing"); return; }
    if (!quiet) setStatus("loading");
    try {
      const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_live_match`, {
        method: "POST",
        headers: { apikey: PUBLISHABLE_KEY, "Content-Type": "application/json" },
        body: JSON.stringify({ code: clean }),
        cache: "no-store",
      });
      if (!response.ok) throw new Error("request failed");
      const rows = await response.json() as LiveMatch[];
      if (!rows.length) { setMatch(null); setStatus("missing"); return; }
      setCode(clean); setMatch(rows[0]); setStatus("watching");
      const url = new URL(window.location.href);
      url.searchParams.set("code", clean);
      window.history.replaceState({}, "", url);
    } catch { if (!quiet) setStatus("error"); }
  }, []);

  useEffect(() => {
    const initial = new URLSearchParams(window.location.search).get("code")?.toUpperCase();
    if (initial) { setCode(initial); void load(initial); }
  }, [load]);

  useEffect(() => {
    if (status !== "watching" || !code) return;
    const timer = window.setInterval(() => void load(code, true), 1200);
    return () => window.clearInterval(timer);
  }, [code, load, status]);

  if (!match || status !== "watching") return <main className="spectator-join">
    <section>
      <div className="spectator-mark">RS</div>
      <p>RACKET SCORE LIVE</p>
      <h1>Follow the match</h1>
      <span>Enter the eight-character code shown on the scorer&apos;s phone.</span>
      <form onSubmit={event => { event.preventDefault(); void load(code); }}>
        <input value={code} onChange={event => setCode(event.target.value.toUpperCase().replace(/[^A-Z2-9]/g, "").slice(0, 8))} placeholder="MATCH CODE" autoCapitalize="characters" autoCorrect="off" aria-label="Live match code" />
        <button disabled={status === "loading"}>{status === "loading" ? "Connecting…" : "Watch live"}</button>
      </form>
      {status === "missing" && <strong>That match could not be found. Check the code and try again.</strong>}
      {status === "error" && <strong>Could not connect. Check your internet connection and try again.</strong>}
    </section>
  </main>;

  const snapshot = match.snapshot;
  const servingMe = snapshot.server === "ME";
  const serverPosition = `${snapshot.server === "OPPONENT" ? "BACK " : ""}${snapshot.serverCourt}`;
  return <main className="spectator-board">
    <header><span><b>●</b> LIVE</span><strong>RACKET SCORE</strong><i>{match.public_code}</i></header>
    <section className={`spectator-side opponent ${!servingMe ? "serving" : ""}`}>
      <label>OPPONENT</label><strong>{snapshot.opponentScore}</strong>
      {!servingMe && <div>{snapshot.sport === "PICKLEBALL_DOUBLES" ? `SERVER ${snapshot.serverNumber} · ` : ""}{serverPosition}</div>}
    </section>
    <div className="spectator-net">NET</div>
    <section className={`spectator-side me ${servingMe ? "serving" : ""}`}>
      <label>MY SIDE</label><strong>{snapshot.meScore}</strong>
      {servingMe && <div>{snapshot.sport === "PICKLEBALL_DOUBLES" ? `SERVER ${snapshot.serverNumber} · ` : ""}{serverPosition}</div>}
    </section>
    <footer>Updates automatically · Last score {new Date(match.updated_at).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</footer>
    {snapshot.winner && <div className="spectator-winner"><span>🏆</span><h1>{snapshot.winner === "ME" ? "MY SIDE" : "OPPONENT"} WINS!</h1><p>{snapshot.meScore} – {snapshot.opponentScore}</p></div>}
  </main>;
}
