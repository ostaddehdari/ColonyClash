# Stage 04 — Mini Game SDK / v0.5.0

## Goal
One authoritative match pipeline for every current and future mini-game:

`Create → Join → Seed → Canonical State → Action Log → Validation → Score → Reward → Rematch`

## Initial game pack
### 1. Color War 10
- 2 players, 5×5, 10 total actions.
- Claim an empty cell; adjacent enemy cells can be captured when surrounded orthogonally by at least two cells owned by the mover.
- Highest owned-cell count wins.

### 2. Number Grab 10
- 2 players, 4×4 board of deterministic values 1–9.
- Values are visible; players alternate choosing one unclaimed cell.
- After 10 total actions, highest sum wins.

### 3. Treasure Flip 10
- 2 players, 4×4 hidden board.
- Each flip reveals a deterministic server-seeded reward from -2 to +5.
- Unopened values are removed from `publicState`; clients cannot see future tiles.
- Highest total after 10 flips wins.

All three use exactly the same match storage, action idempotency, reward settlement and rematch APIs.

## Server authority
Clients never submit score, result or reward. They submit only an action intent. The server:
1. locks the match row;
2. checks player membership and turn;
3. deduplicates `clientActionId`;
4. runs the registered pure deterministic engine;
5. stores action sequence + SHA-256 state hash;
6. updates canonical state/current turn;
7. computes score itself;
8. settles rewards once using ledger idempotency keys.

## Default rewards
- win: 40 Coin + 30 XP
- draw: 20 Coin + 20 XP
- loss: 10 Coin + 10 XP

## API
- `GET /games`
- `POST /games/matches`
- `POST /games/matches/:id/join`
- `GET /games/matches/:id`
- `POST /games/matches/:id/actions`
- `GET /games/matches/:id/actions`
- `POST /games/matches/:id/rematch`
