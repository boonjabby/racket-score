"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { announcement, applyRally, GameState, newGame, scorerCourt, serviceCourt, Side, Sport, SPORTS, tennisDisplay } from "./scoring";

type Mode = "player" | "umpire";
const DEFAULT_NAMES = { me: "My side", opponent: "Opponent" };
const MATCH_STORAGE_KEY = "racket-score-active-match-v1";
type InstallPromptEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: "accepted" | "dismissed" }> };
type WakeLockHandle = EventTarget & { released: boolean; release: () => Promise<void> };

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
  const [vibration, setVibration] = useState(true);
  const [keepAwake, setKeepAwake] = useState(false);
  const [wakeSupported, setWakeSupported] = useState(true);
  const [settings, setSettings] = useState(false);
  const [setup, setSetup] = useState(false);
  const [menu, setMenu] = useState(false);
  const [firstServer, setFirstServer] = useState<Side>("me");
  const [firstNumber, setFirstNumber] = useState<1 | 2>(2);
  const [hydrated, setHydrated] = useState(false);
  const [restored, setRestored] = useState(false);
  const [installPrompt, setInstallPrompt] = useState<InstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(false);
  const [updateReady, setUpdateReady] = useState<ServiceWorker | null>(null);
  const wakeLock = useRef<WakeLockHandle | null>(null);

  /* Hydrate device state once from browser APIs and local storage. */
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register(`${basePath}/sw.js`).then(registration => {
        if (registration.waiting) setUpdateReady(registration.waiting);
        registration.addEventListener("updatefound", () => {
          const worker = registration.installing;
          worker?.addEventListener("statechange", () => {
            if (worker.state === "installed" && navigator.serviceWorker.controller) setUpdateReady(worker);
          });
        });
      }).catch(() => undefined);
      navigator.serviceWorker.addEventListener("controllerchange", () => window.location.reload());
    }
    const onInstallPrompt = (event: Event) => { event.preventDefault(); setInstallPrompt(event as InstallPromptEvent); };
    const onInstalled = () => { setInstalled(true); setInstallPrompt(null); };
    window.addEventListener("beforeinstallprompt", onInstallPrompt);
    window.addEventListener("appinstalled", onInstalled);
    setInstalled(window.matchMedia("(display-mode: standalone)").matches || Boolean((navigator as Navigator & { standalone?: boolean }).standalone));
    const saved = localStorage.getItem("racket-score-preferences");
    if (saved) {
      try {
        const prefs = JSON.parse(saved);
        if (prefs.names) setNames(prefs.names);
        if (typeof prefs.sound === "boolean") setSound(prefs.sound);
        if (typeof prefs.vibration === "boolean") setVibration(prefs.vibration);
        if (typeof prefs.keepAwake === "boolean") setKeepAwake(prefs.keepAwake);
      } catch { /* Ignore corrupt local preferences. */ }
    }
    const activeMatch = localStorage.getItem(MATCH_STORAGE_KEY);
    if (activeMatch) {
      try {
        const snapshot = JSON.parse(activeMatch);
        if (snapshot.game?.sport && snapshot.sport) {
          if (!snapshot.game.serverCourt) snapshot.game.serverCourt = serviceCourt(snapshot.game);
          setGame(snapshot.game);
          setHistory(Array.isArray(snapshot.history) ? snapshot.history : []);
          setSport(snapshot.sport);
          if (snapshot.mode === "player" || snapshot.mode === "umpire") setMode(snapshot.mode);
          setRestored(snapshot.game.scores?.me > 0 || snapshot.game.scores?.opponent > 0 || snapshot.history?.length > 0);
        }
      } catch { localStorage.removeItem(MATCH_STORAGE_KEY); }
    }
    setWakeSupported("wakeLock" in navigator);
    setHydrated(true);
    return () => {
      window.removeEventListener("beforeinstallprompt", onInstallPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    if (!hydrated) return;
    localStorage.setItem("racket-score-preferences", JSON.stringify({ names, sound, vibration, keepAwake }));
  }, [names, sound, vibration, keepAwake, hydrated]);

  useEffect(() => {
    if (!hydrated || !keepAwake || !wakeSupported) return;
    let cancelled = false;
    const requestLock = async () => {
      if (cancelled || document.visibilityState !== "visible" || wakeLock.current) return;
      try {
        const lock = await (navigator as Navigator & { wakeLock: { request: (type: "screen") => Promise<WakeLockHandle> } }).wakeLock.request("screen");
        if (cancelled) { await lock.release(); return; }
        wakeLock.current = lock;
        lock.addEventListener("release", () => { if (wakeLock.current === lock) wakeLock.current = null; });
      } catch { /* The phone may reject wake lock in power-saving mode. */ }
    };
    const onVisibility = () => { if (document.visibilityState === "visible") void requestLock(); };
    document.addEventListener("visibilitychange", onVisibility);
    void requestLock();
    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", onVisibility);
      const lock = wakeLock.current;
      wakeLock.current = null;
      void lock?.release();
    };
  }, [keepAwake, wakeSupported, hydrated]);
  useEffect(() => {
    if (!hydrated) return;
    localStorage.setItem(MATCH_STORAGE_KEY, JSON.stringify({ game, history, sport, mode, savedAt: Date.now() }));
  }, [game, history, sport, mode, hydrated]);

  const sportInfo = useMemo(() => SPORTS.find(item => item.id === sport)!, [sport]);
  const display = (side: Side) => sport === "tennis" ? tennisDisplay(game.tennisPoints, side) : game.scores[side];

  function score(side: Side) {
    if (game.winner) return;
    const next = applyRally(game, side);
    setHistory(items => [...items, game]);
    setGame(next);
    if (vibration && navigator.vibrate) navigator.vibrate(35);
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

  async function installApp() {
    if (!installPrompt) return;
    await installPrompt.prompt();
    const choice = await installPrompt.userChoice;
    if (choice.outcome === "accepted") setInstallPrompt(null);
  }

  function applyUpdate() {
    updateReady?.postMessage({ type: "SKIP_WAITING" });
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
        <span className="save-state" aria-label="Match saved on this device"><b /> Saved</span>
        <button className="icon settings-icon" onClick={() => setSettings(true)} aria-label="Open settings">⚙</button>
        <button className="icon" onClick={() => setMenu(!menu)} aria-label="Open game options">•••</button>
      </div>
    </header>

    {menu && <div className="menu-card">
      <p>Choose a sport</p>
      <div className="sport-list">{SPORTS.map(item => <button className={item.id === sport ? "selected" : ""} key={item.id} onClick={() => chooseSport(item.id)}>{item.label}<span>{item.id === sport ? "✓" : ""}</span></button>)}</div>
      <div className="menu-row"><span>View</span><div className="segmented"><button className={mode === "player" ? "selected" : ""} onClick={() => setMode("player")}>Player</button><button className={mode === "umpire" ? "selected" : ""} onClick={() => setMode("umpire")}>Umpire</button></div></div>
      <button className="watch-toggle" onClick={() => { setWatch(!watch); setMenu(false); }}>{watch ? "Exit watch preview" : "Preview on watch"}<span>›</span></button>
      {!installed && installPrompt && <button className="install-button" onClick={installApp}><span><b>↓</b><i>Install Racket Score</i><small>Add it to this phone for quick access</small></span><strong>Install</strong></button>}
      {installed && <div className="installed-note"><span>✓</span> Installed on this device</div>}
      <div className="name-fields"><label>Your label<input value={names.me} onChange={e => setNames({ ...names, me: e.target.value || "My side" })} /></label><label>Opponent label<input value={names.opponent} onChange={e => setNames({ ...names, opponent: e.target.value || "Opponent" })} /></label></div>
    </div>}

    <section className="score-shell">{court}</section>
    {restored && <div className="toast" role="status"><span>↻</span><div><strong>Match restored</strong><small>Your score was saved on this device.</small></div><button onClick={() => setRestored(false)} aria-label="Dismiss">×</button></div>}
    {updateReady && <div className="update-bar" role="status"><div><strong>Update ready</strong><span>Refresh when you’re between rallies.</span></div><button onClick={applyUpdate}>Update now</button></div>}
    {watch && <button className="exit-watch" onClick={() => setWatch(false)}>Exit watch preview</button>}

    {setup && <div className="modal-backdrop" role="presentation"><section className="modal" role="dialog" aria-modal="true" aria-labelledby="setup-title">
      <button className="close" onClick={() => setSetup(false)} aria-label="Close">×</button>
      <div className="modal-kicker">NEW GAME</div><h1 id="setup-title">Who serves first?</h1><p>Set the opening service, then step onto court.</p>
      <div className="serve-choice"><button className={firstServer === "me" ? "selected" : ""} onClick={() => setFirstServer("me")}><span>YOU</span>{names.me}<i>✓</i></button><button className={firstServer === "opponent" ? "selected" : ""} onClick={() => setFirstServer("opponent")}><span>THEM</span>{names.opponent}<i>✓</i></button></div>
      {sport === "pickleball-doubles" && <div className="server-number"><span>Starting server number</span><div className="segmented"><button className={firstNumber === 1 ? "selected" : ""} onClick={() => setFirstNumber(1)}>1</button><button className={firstNumber === 2 ? "selected" : ""} onClick={() => setFirstNumber(2)}>2</button></div><small>Official games normally open at 0–0–2.</small></div>}
      <button className="start" onClick={begin}>Start game <span>→</span></button>
    </section></div>}

    {settings && <div className="modal-backdrop" role="presentation"><section className="modal settings-modal" role="dialog" aria-modal="true" aria-labelledby="settings-title">
      <button className="close" onClick={() => setSettings(false)} aria-label="Close settings">×</button>
      <div className="modal-kicker">MATCH SETTINGS</div><h1 id="settings-title">Court preferences</h1><p>These choices are saved on this phone.</p>
      <div className="setting-list">
        <SettingToggle title="Read scores aloud" detail="Announce every score from the server’s point of view." checked={sound} onChange={setSound} />
        <SettingToggle title="Vibration feedback" detail="Give a short vibration when a rally is recorded." checked={vibration} onChange={setVibration} />
        <SettingToggle title="Keep screen awake" detail={wakeSupported ? "Prevent the screen from sleeping while this app is open." : "This browser does not support screen wake lock."} checked={keepAwake && wakeSupported} disabled={!wakeSupported} onChange={setKeepAwake} />
      </div>
      <button className="start settings-done" onClick={() => setSettings(false)}>Done <span>✓</span></button>
    </section></div>}

    {game.winner && !setup && <div className="winner" role="dialog" aria-modal="true"><Confetti /><div className="trophy">🏆</div><div className="modal-kicker">MATCH COMPLETE</div><h1>{names[game.winner]} win!</h1><p>{game.scores.me} – {game.scores.opponent}</p><button className="start" onClick={() => setSetup(true)}>Next game <span>→</span></button></div>}
  </main>;
}

function SettingToggle({ title, detail, checked, disabled = false, onChange }: { title: string; detail: string; checked: boolean; disabled?: boolean; onChange: (value: boolean) => void }) {
  return <button className="setting-row" role="switch" aria-checked={checked} disabled={disabled} onClick={() => onChange(!checked)}>
    <span><strong>{title}</strong><small>{detail}</small></span><i className={checked ? "toggle on" : "toggle"}><b /></i>
  </button>;
}

function ScoreSide({ side, label, value, serving, onScore, game }: { side: Side; label: string; value: string | number; serving: boolean; onScore: (side: Side) => void; game: GameState }) {
  const court = scorerCourt(game);
  const courtLabel = game.server === "opponent" ? `back ${court}` : court;
  return <button className={`score-side ${side} ${serving ? "serving" : ""}`} onClick={() => onScore(side)} aria-label={`Point to ${label}. Score ${value}`}>
    <div className="side-top"><span className="side-label">{label}</span>{serving && <span className="serve-pill"><b>●</b> SERVE</span>}</div>
    <strong className="score">{value}</strong>
    {serving && <div className={`service-detail court-${court}`}><span>{courtLabel} court</span>{game.sport === "pickleball-doubles" && <b>SERVER {game.serverNumber}</b>}</div>}
    <span className="tap-hint">TAP TO ADD POINT</span>
  </button>;
}

function Confetti() {
  return <div className="confetti" aria-hidden="true">{Array.from({ length: 28 }, (_, i) => <i key={i} style={{ "--i": i } as React.CSSProperties} />)}</div>;
}
