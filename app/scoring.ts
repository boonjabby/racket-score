export type Side = "me" | "opponent";
export type CourtSide = "left" | "right";
export type Sport = "pickleball-doubles" | "pickleball-singles" | "tennis" | "badminton" | "table-tennis" | "squash" | "racquetball";

export type GameState = {
  sport: Sport;
  scores: Record<Side, number>;
  tennisPoints: Record<Side, number>;
  server: Side;
  serverNumber: 1 | 2;
  serverCourt: CourtSide;
  openingServe: boolean;
  winner: Side | null;
};

export const SPORTS: { id: Sport; label: string; short: string }[] = [
  { id: "pickleball-doubles", label: "Pickleball doubles", short: "Pickleball" },
  { id: "pickleball-singles", label: "Pickleball singles", short: "Singles" },
  { id: "tennis", label: "Tennis / padel", short: "Tennis" },
  { id: "badminton", label: "Badminton", short: "Badminton" },
  { id: "table-tennis", label: "Table tennis", short: "Table tennis" },
  { id: "squash", label: "Squash", short: "Squash" },
  { id: "racquetball", label: "Racquetball", short: "Racquetball" },
];

export function newGame(sport: Sport, server: Side, serverNumber: 1 | 2 = 2): GameState {
  return { sport, scores: { me: 0, opponent: 0 }, tennisPoints: { me: 0, opponent: 0 }, server, serverNumber, serverCourt: "right", openingServe: sport === "pickleball-doubles" && serverNumber === 2, winner: null };
}

const other = (side: Side): Side => side === "me" ? "opponent" : "me";
const oppositeCourt = (court: CourtSide): CourtSide => court === "right" ? "left" : "right";

function targetFor(sport: Sport) {
  if (sport.startsWith("pickleball")) return 11;
  if (sport === "badminton") return 21;
  if (sport === "table-tennis") return 11;
  if (sport === "squash") return 11;
  return 15;
}

function hasWon(score: number, rival: number, target: number, cap?: number) {
  return score >= target && score - rival >= 2 || Boolean(cap && score >= cap);
}

export function applyRally(state: GameState, rallyWinner: Side): GameState {
  if (state.winner) return state;
  const next: GameState = JSON.parse(JSON.stringify(state));

  if (state.sport === "tennis") {
    const loser = other(rallyWinner);
    next.tennisPoints[rallyWinner] += 1;
    if (next.tennisPoints[rallyWinner] >= 4 && next.tennisPoints[rallyWinner] - next.tennisPoints[loser] >= 2) {
      next.scores[rallyWinner] += 1;
      next.tennisPoints = { me: 0, opponent: 0 };
      next.server = other(next.server);
      if (hasWon(next.scores[rallyWinner], next.scores[loser], 6)) next.winner = rallyWinner;
    }
    return next;
  }

  if (state.sport === "pickleball-doubles") {
    if (rallyWinner === state.server) {
      next.scores[rallyWinner] += 1;
      next.serverCourt = oppositeCourt(serviceCourt(state));
    } else if (state.openingServe) {
      next.server = rallyWinner;
      next.serverNumber = 1;
      next.serverCourt = "right";
      next.openingServe = false;
    } else if (state.serverNumber === 1) {
      next.serverNumber = 2;
      next.serverCourt = oppositeCourt(serviceCourt(state));
    } else {
      next.server = rallyWinner;
      next.serverNumber = 1;
      next.serverCourt = "right";
    }
  } else if (state.sport === "pickleball-singles" || state.sport === "racquetball") {
    if (rallyWinner === state.server) next.scores[rallyWinner] += 1;
    else next.server = rallyWinner;
  } else {
    next.scores[rallyWinner] += 1;
    const total = next.scores.me + next.scores.opponent;
    const interval = state.sport === "table-tennis" ? (Math.max(next.scores.me, next.scores.opponent) >= 10 ? 1 : 2) : 1;
    if (total % interval === 0) next.server = other(next.server);
  }

  const rival = other(rallyWinner);
  const target = targetFor(state.sport);
  const cap = state.sport === "badminton" ? 30 : undefined;
  if (hasWon(next.scores[rallyWinner], next.scores[rival], target, cap)) next.winner = rallyWinner;
  return next;
}

export function serviceCourt(state: GameState): CourtSide {
  if (state.sport === "pickleball-doubles" && state.serverCourt) return state.serverCourt;
  return state.scores[state.server] % 2 === 0 ? "right" : "left";
}

/** Physical court as seen by the scorer at the near baseline. */
export function scorerCourt(state: GameState): CourtSide {
  const court = serviceCourt(state);
  return state.server === "opponent" ? oppositeCourt(court) : court;
}

export function tennisDisplay(points: Record<Side, number>, side: Side) {
  const mine = points[side], theirs = points[other(side)];
  if (mine >= 3 && theirs >= 3) return mine === theirs ? "40" : mine > theirs ? "AD" : "40";
  return ["0", "15", "30", "40"][Math.min(mine, 3)];
}

export function announcement(state: GameState, names: Record<Side, string>) {
  const receiving = other(state.server);
  const score = state.sport === "tennis"
    ? `${tennisDisplay(state.tennisPoints, state.server)}, ${tennisDisplay(state.tennisPoints, receiving)}`
    : `${state.scores[state.server]}, ${state.scores[receiving]}${state.sport === "pickleball-doubles" ? `, ${state.serverNumber}` : ""}`;
  return `${score}. ${names[state.server]} serves.`;
}
