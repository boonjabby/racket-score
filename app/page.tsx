"use client";

import { useEffect, useMemo, useState } from "react";
import { announcement, applyRally, GameState, newGame, serviceCourt, Side, Sport, SPORTS, tennisDisplay } from "./scoring";

type Mode = "player" | "umpire";
const DEFAULT_NAMES = { me: "My side", opponent: "Opponent" };

function speak(text: string, enabled: boolean) {
  if (!enabled || typeof window === "undefined" || !("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.rate = .92;
  window.speechSynthesis.speak(utterance);
}

export default function Home() {
  const [sport, setSport] = useState<Sport>("pickleball-doubles");
  const [game, setGame] = useState<GameState>(() => newGame("pickleball-doubles", "me", 2));
  const [history, setHistory] = useState<GameState[]>([]);
  const [names, setNames] = useState(DEFAULT_NAMES);
  const [mode, setMode] = useState<Mode>("player");
  const [watch, setWatch] = useState(false);
  const [sound, setSound] = useState(true);
  const [setup, setSetup] = useState(false);
  const [menu, setMenu] = useState(false);
  const [firstServer, setFirstServer] = useState<Side>("me");
  const [firstNumber, setFirstNumber] = useState<1 | 2>(2);

  useEffect(() => {
    const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";
    if ("serviceWorker" in navigator) navigator.serviceWorker.register(`${basePath}/sw.js`).catch(() => undefined);
    const saved = localStorage.getItem("racket-score-preferences");
    if (saved) {
      try {
        const prefs = JSON.parse(saved);
        if (prefs.names) setNames(prefs.names);
        if (typeof prefs.sound === "boolean") setSound(prefs.sound);
      } catch { /* Ignore corrupt local preferences. */ }
    }
  }, []);

  useEffect(() => { localStorage.setItem("racket-score-preferences", JSON.stringify({ names, sound })); }, [names, sound]);

  const sportInfo = useMemo(() => SPORTS.find(item => item.id === sport)!, [sport]);
  const display = (side: Side) => sport === "tennis" ? tennisDisplay(game.tennisPoints, side) : game.scores[side];

  function score(side: Side) {
    if (game.winner) return;
    const next = applyRally(game, side);
    setHistory(items => [...items, game]);
    setGame(next);
    if (navigator.vibrate) navigator.vibrate(35);
    if (next.winner) speak(`${names[next.winner]} wins.`, sound);
    else speak(announcement(next, names), sound);
  }

  function undo() {
    const previous = history.at(-1);
    if (!previous) return;
    setGame(previous);
    setHistory(items => items.slice(0, -1));
  }

  function begin() {
    const next = newGame(sport, firstServer, firstNumber);
    setGame(next); setHistory([]); setSetup(false); setMenu(false);
    speak(announcement(next, names), sound);
  }

  function chooseSport(nextSport: Sport) {
    setSport(nextSport); setGame(newGame(nextSport, firstServer, firstNumber)); setHistory([]); setMenu(false);
  }

  const centerControls = <div className="net-controls">
    <button onClick={undo} disabled={!history.length} aria-label="Undo last rally" title="Undo">↶</button>
    <span className="net-label">NET</span>
    <button onClick={() => setSetup(true)} aria-label="Set up a new game" title="New game">⟳</button>
  </div>;

  const court = mode === "player" ? <div className="court player-court">
    <ScoreSide side="opponent" label={names.opponent} value={display("opponent")} serving={game.server === "opponent"} onScore={score} game={game} />
    <div className="net">{centerControls}</div>
    <ScoreSide side="me" label={names.me} value={display("me")} serving={game.server === "me"} onScore={score} game={game} />
  </div> : <div className="court umpire-court">
    <ScoreSide side="opponent" label={names.opponent} value={display("opponent")} serving={game.server === "opponent"} onScore={score} game={game} />
    <div className="net vertical">{centerControls}</div>
    <ScoreSide side="me" label={names.me} value={display("me")} serving={game.server === "me"} onScore={score} game={game} />
  </div>;

  return <main className={watch ? "app watch-preview" : "app"}>
    <header>
      <button className="brand" onClick={() => setMenu(!menu)} aria-expanded={menu}><span>RS</span><strong>{sportInfo.short}</strong><i>⌄</i></button>
      <div className="header-actions">
        <button className={sound ? "icon active" : "icon"} onClick={() => setSound(!sound)} aria-label={sound ? "Mute score announcements" : "Enable score announcements"}>{sound ? "◖))" : "◖×"}</button>
        <button className="icon" onClick={() => setMenu(!menu)} aria-label="Open game options">•••</button>
      </div>
    </header>

    {menu && <div className="menu-card">
      <p>Choose a sport</p>
      <div className="sport-list">{SPORTS.map(item => <button className={item.id === sport ? "selected" : ""} key={item.id} onClick={() => chooseSport(item.id)}>{item.label}<span>{item.id === sport ? "✓" : ""}</span></button>)}</div>
      <div className="menu-row"><span>View</span><div className="segmented"><button className={mode === "player" ? "selected" : ""} onClick={() => setMode("player")}>Player</button><button className={mode === "umpire" ? "selected" : ""} onClick={() => setMode("umpire")}>Umpire</button></div></div>
      <button className="watch-toggle" onClick={() => { setWatch(!watch); setMenu(false); }}>{watch ? "Exit watch preview" : "Preview on watch"}<span>›</span></button>
      <div className="name-fields"><label>Your label<input value={names.me} onChange={e => setNames({ ...names, me: e.target.value || "My side" })} /></label><label>Opponent label<input value={names.opponent} onChange={e => setNames({ ...names, opponent: e.target.value || "Opponent" })} /></label></div>
    </div>}

    <section className="score-shell">{court}</section>
    {watch && <button className="exit-watch" onClick={() => setWatch(false)}>Exit watch preview</button>}

    {setup && <div className="modal-backdrop" role="presentation"><section className="modal" role="dialog" aria-modal="true" aria-labelledby="setup-title">
      <button className="close" onClick={() => setSetup(false)} aria-label="Close">×</button>
      <div className="modal-kicker">NEW GAME</div><h1 id="setup-title">Who serves first?</h1><p>Set the opening service, then step onto court.</p>
      <div className="serve-choice"><button className={firstServer === "me" ? "selected" : ""} onClick={() => setFirstServer("me")}><span>YOU</span>{names.me}<i>✓</i></button><button className={firstServer === "opponent" ? "selected" : ""} onClick={() => setFirstServer("opponent")}><span>THEM</span>{names.opponent}<i>✓</i></button></div>
      {sport === "pickleball-doubles" && <div className="server-number"><span>Starting server number</span><div className="segmented"><button className={firstNumber === 1 ? "selected" : ""} onClick={() => setFirstNumber(1)}>1</button><button className={firstNumber === 2 ? "selected" : ""} onClick={() => setFirstNumber(2)}>2</button></div><small>Official games normally open at 0–0–2.</small></div>}
      <button className="start" onClick={begin}>Start game <span>→</span></button>
    </section></div>}

    {game.winner && !setup && <div className="winner" role="dialog" aria-modal="true"><Confetti /><div className="trophy">🏆</div><div className="modal-kicker">MATCH COMPLETE</div><h1>{names[game.winner]} win!</h1><p>{game.scores.me} – {game.scores.opponent}</p><button className="start" onClick={() => setSetup(true)}>Next game <span>→</span></button></div>}
  </main>;
}

function ScoreSide({ side, label, value, serving, onScore, game }: { side: Side; label: string; value: string | number; serving: boolean; onScore: (side: Side) => void; game: GameState }) {
  return <button className={`score-side ${side} ${serving ? "serving" : ""}`} onClick={() => onScore(side)} aria-label={`Point to ${label}. Score ${value}`}>
    <div className="side-top"><span className="side-label">{label}</span>{serving && <span className="serve-pill"><b>●</b> SERVE</span>}</div>
    <strong className="score">{value}</strong>
    {serving && <div className="service-detail"><span>{serviceCourt(game)} court</span>{game.sport === "pickleball-doubles" && <b>SERVER {game.serverNumber}</b>}</div>}
    <span className="tap-hint">TAP TO ADD POINT</span>
  </button>;
}

function Confetti() {
  return <div className="confetti" aria-hidden="true">{Array.from({ length: 28 }, (_, i) => <i key={i} style={{ "--i": i } as React.CSSProperties} />)}</div>;
}
