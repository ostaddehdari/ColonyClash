# Stage 06 API — Colony War

Base path: `/api/v1/wars` (or the project global API prefix).

## Core flow
1. `POST /wars` — owner/captain declares a war.
2. `POST /wars/:id/accept` — defender owner/captain accepts.
3. `POST /wars/:id/roster` — each colony sets its fighters.
4. `POST /wars/:id/start` — creates the ordered multi-round war and launches round 1.
5. Fighters play the generated normal game match through the Stage 04/05 match APIs.
6. `POST /wars/:id/sync` — settles completed rounds, updates live score, launches the next round, and finalizes the war.
7. `POST /wars/:id/rematch` — creates the revenge declaration with sides flipped from the requesting commander's colony.

## Realtime `/rt`
- `war.join { warId }`
- `war.heartbeat { warId }`
- `war.emote { warId, code }`
- `war.score { warId }`

Spectator rooms are public only while the war is active/finished. Emotes are persisted and rate-limited.

## Scoring
- Round win: 1 war point for the winner.
- Round draw: 1 point for each colony.
- Finished war winner: +100 colony war/season points.
- Both participating colonies: +25 participation points.
- MVP ranking: round wins weighted at 100 plus raw mini-game score.

## Security
- Only colony owner/captain can declare, accept, set roster, start, cancel, or request a rematch.
- Roster members must already belong to that colony.
- Round results are derived from server-authoritative `matches` / `match_players` records, never from client-submitted war scores.
