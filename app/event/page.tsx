"use client";

import { useCallback, useEffect, useState } from "react";
import "./event.css";
import "./guest.css";

const SUPABASE_URL = "https://qpvxeucamyaupnxepwdl.supabase.co";
const PUBLISHABLE_KEY = "sb_publishable_tv5LzPDm-aXMz4j_4Ll4dA_TVQGkcDH";
const CODE_PATTERN = /^[A-HJ-NP-Z2-9]{8}$/;

type Player = { id: number; name: string; games: number; wins: number };
type Court = { number: number; teamA: number[]; teamB: number[]; winner?: number; gameNumber: number };
type EventSnapshot = { kind: "round_robin"; title: string; completedGames: number; players: Player[]; courts: Court[]; waiting: number[]; paused: number[] };
type EventRow = { public_code: string; sequence: number; snapshot: EventSnapshot; updated_at: string };

export default function LiveEventPage() {
  const [code, setCode] = useState("");
  const [event, setEvent] = useState<EventRow | null>(null);
  const [status, setStatus] = useState<"enter" | "loading" | "watching" | "missing" | "error">("enter");
  const [playerName, setPlayerName] = useState("");
  const [joinStatus, setJoinStatus] = useState<"idle" | "joining" | "pending" | "error">("idle");

  async function joinEvent() {
    const name = playerName.trim();
    if (!name || !code) return;
    setJoinStatus("joining");
    try {
      const stored = localStorage.getItem("racket-score-event-auth");
      let auth = stored ? JSON.parse(stored) as { access_token?: string } : null;
      if (!auth?.access_token) {
        const signup = await fetch(`${SUPABASE_URL}/auth/v1/signup`, { method: "POST", headers: { apikey: PUBLISHABLE_KEY, "Content-Type": "application/json" }, body: "{}" });
        if (!signup.ok) throw new Error("auth");
        auth = await signup.json();
        localStorage.setItem("racket-score-event-auth", JSON.stringify(auth));
      }
      const accessToken = auth?.access_token;
      if (!accessToken) throw new Error("auth");
      const joined = await fetch(`${SUPABASE_URL}/rest/v1/rpc/join_round_robin`, { method: "POST", headers: { apikey: PUBLISHABLE_KEY, Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" }, body: JSON.stringify({ code, player_name: name }) });
      if (!joined.ok) throw new Error("join");
      localStorage.setItem(`racket-score-event-name-${code}`, name);
      setJoinStatus("pending");
    } catch { setJoinStatus("error"); }
  }

  const load = useCallback(async (requested: string, quiet = false) => {
    const clean = requested.trim().toUpperCase();
    if (!CODE_PATTERN.test(clean)) { setStatus("missing"); return; }
    if (!quiet) setStatus("loading");
    try {
      const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_live_match`, {
        method: "POST", headers: { apikey: PUBLISHABLE_KEY, "Content-Type": "application/json" },
        body: JSON.stringify({ code: clean }), cache: "no-store",
      });
      if (!response.ok) throw new Error("request failed");
      const rows = await response.json() as EventRow[];
      if (!rows.length || rows[0].snapshot.kind !== "round_robin") { setEvent(null); setStatus("missing"); return; }
      setCode(clean); setEvent(rows[0]); setStatus("watching");
      const url = new URL(window.location.href); url.searchParams.set("code", clean); window.history.replaceState({}, "", url);
    } catch { if (!quiet) setStatus("error"); }
  }, []);

  useEffect(() => { const initial = new URLSearchParams(window.location.search).get("code"); if (initial) { setCode(initial.toUpperCase()); void load(initial); } }, [load]);
  useEffect(() => { if (status !== "watching" || !code) return; const timer = window.setInterval(() => void load(code, true), 1500); return () => window.clearInterval(timer); }, [code, load, status]);

  if (!event || status !== "watching") return <main className="event-join"><section><div className="event-mark">RS</div><p>RACKET SCORE EVENT</p><h1>Follow the courts</h1><span>Enter the eight-character Round Robin code from the organiser.</span><form onSubmit={e => { e.preventDefault(); void load(code); }}><input value={code} onChange={e => setCode(e.target.value.toUpperCase().replace(/[^A-Z2-9]/g, "").slice(0, 8))} placeholder="EVENT CODE" aria-label="Event code"/><button disabled={status === "loading"}>{status === "loading" ? "Connecting…" : "View event"}</button></form>{status === "missing" && <strong>That event has expired or the code is incorrect.</strong>}{status === "error" && <strong>Could not connect. Check your internet and try again.</strong>}</section></main>;

  const data = event.snapshot;
  const names = new Map(data.players.map(player => [player.id, player.name]));
  const team = (ids: number[]) => ids.map(id => names.get(id) ?? `Player ${id + 1}`).join(" + ");
  return <main className="event-board">
    <header><div><span>● LIVE EVENT</span><h1>{data.title || "Round Robin"}</h1></div><b>{event.public_code}</b></header>
    <section className="event-summary"><span>{data.courts.length} courts</span><span>{data.completedGames} games completed</span><span>Updated {new Date(event.updated_at).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</span></section>
    <section className="event-join-card"><div><strong>PLAYING TODAY?</strong><p>Join with a temporary name so the organiser can assign this device.</p></div>{joinStatus === "pending" ? <b>✓ REQUEST SENT</b> : <form onSubmit={e => { e.preventDefault(); void joinEvent(); }}><input value={playerName} maxLength={40} onChange={e => setPlayerName(e.target.value)} placeholder="Your name or player number"/><button disabled={!playerName.trim() || joinStatus === "joining"}>{joinStatus === "joining" ? "Joining…" : "Request to join"}</button></form>}{joinStatus === "error" && <em>Could not join. Please try again.</em>}</section>
    <section className="event-courts">{data.courts.map(court => <article key={court.number}><div className="court-title"><strong>COURT {court.number}</strong><span>GAME {court.gameNumber}</span></div><div className={court.winner === 0 ? "team winner" : "team"}><span>{team(court.teamA)}</span>{court.winner === 0 && <b>WINNER</b>}</div><i>VS</i><div className={court.winner === 1 ? "team winner" : "team"}><span>{team(court.teamB)}</span>{court.winner === 1 && <b>WINNER</b>}</div></article>)}</section>
    <section className="event-queue"><div><strong>WAITING TO PLAY</strong><p>{data.waiting.length ? data.waiting.map(id => names.get(id)).join(" · ") : "No players waiting"}</p></div>{data.paused?.length > 0 && <div><strong>PAUSED</strong><p>{data.paused.map(id => names.get(id)).join(" · ")}</p></div>}</section>
    <section className="event-table"><h2>Session standings</h2>{[...data.players].sort((a,b) => b.wins-a.wins || a.games-b.games).map(player => <div key={player.id}><span>{player.name}</span><b>{player.wins} wins · {player.games} games</b></div>)}</section>
    <footer>Read-only live view · The organiser controls this event</footer>
  </main>;
}
