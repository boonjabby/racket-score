import test from "node:test";
import assert from "node:assert/strict";
import { announcement, applyRally, newGame, scorerCourt, serviceCourt } from "../app/scoring.ts";

test("pickleball doubles starts with the official 0-0-2 opening serve", () => {
  const game = newGame("pickleball-doubles", "me", 2);
  assert.equal(announcement(game, { me: "My side", opponent: "Opponent" }), "0, 0, 2. My side serves.");
  const sideOut = applyRally(game, "opponent");
  assert.equal(sideOut.server, "opponent");
  assert.equal(sideOut.serverNumber, 1);
  assert.equal(serviceCourt(sideOut), "right");
  assert.equal(scorerCourt(sideOut), "left");
  assert.deepEqual(sideOut.scores, { me: 0, opponent: 0 });
});

test("a regular doubles service moves through server two before side out", () => {
  let game = newGame("pickleball-doubles", "me", 1);
  game = applyRally(game, "opponent");
  assert.equal(game.server, "me");
  assert.equal(game.serverNumber, 2);
  assert.equal(serviceCourt(game), "left");
  assert.equal(scorerCourt(game), "left");
  game = applyRally(game, "opponent");
  assert.equal(game.server, "opponent");
  assert.equal(game.serverNumber, 1);
});

test("doubles server moves courts after a point and the partner stays opposite", () => {
  let game = newGame("pickleball-doubles", "me", 1);
  game = applyRally(game, "me");
  assert.equal(serviceCourt(game), "left");
  game = applyRally(game, "opponent");
  assert.equal(game.serverNumber, 2);
  assert.equal(serviceCourt(game), "right");
});

test("opponent courts are mirrored from the scorer perspective", () => {
  const opponentStarts = applyRally(newGame("pickleball-doubles", "me", 2), "opponent");
  assert.equal(serviceCourt(opponentStarts), "right");
  assert.equal(scorerCourt(opponentStarts), "left");
  const secondServer = applyRally(opponentStarts, "me");
  assert.equal(serviceCourt(secondServer), "left");
  assert.equal(scorerCourt(secondServer), "right");
});

test("pickleball only awards a point to the serving side", () => {
  const game = newGame("pickleball-singles", "me", 1);
  const receiveWins = applyRally(game, "opponent");
  assert.deepEqual(receiveWins.scores, { me: 0, opponent: 0 });
  const point = applyRally(receiveWins, "opponent");
  assert.equal(point.scores.opponent, 1);
  assert.equal(serviceCourt(point), "left");
});

test("spoken score is ordered from the server perspective", () => {
  const game = { ...newGame("pickleball-doubles", "opponent", 2), scores: { me: 3, opponent: 1 }, openingServe: false };
  assert.equal(announcement(game, { me: "My side", opponent: "Opponent" }), "1, 3, 2. Opponent serves.");
});

test("badminton wins at 21 by two and caps at 30", () => {
  const win = applyRally({ ...newGame("badminton", "me"), scores: { me: 20, opponent: 19 } }, "me");
  assert.equal(win.winner, "me");
  const capped = applyRally({ ...newGame("badminton", "opponent"), scores: { me: 29, opponent: 29 } }, "opponent");
  assert.equal(capped.winner, "opponent");
});

test("tennis games require four points and a two-point margin", () => {
  let game = newGame("tennis", "me");
  for (let i = 0; i < 3; i++) game = applyRally(game, "me");
  for (let i = 0; i < 3; i++) game = applyRally(game, "opponent");
  game = applyRally(game, "me");
  assert.equal(game.scores.me, 0);
  game = applyRally(game, "me");
  assert.equal(game.scores.me, 1);
});
