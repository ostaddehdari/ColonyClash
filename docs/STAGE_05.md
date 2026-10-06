# Stage 05 — Matchmaking / v0.6.0

## Goal
Turn the deterministic mini-game SDK into a real social multiplayer loop:

`Quick Match / Friend Link → Match → Realtime Room → Reconnect → Result → Rating → Rematch`

## Implemented
- Quick Match for live and async games.
- Skill-rating matching with an expanding search window.
- Region preference with `global` fallback.
- PostgreSQL `FOR UPDATE SKIP LOCKED` reservation so multiple API workers can dequeue safely.
- Durable matchmaking tickets with queued/matching/matched/cancelled/expired states.
- Direct match invite codes and deep links.
- Existing public Challenge code can now be accepted and atomically promoted into a real match flow.
- Redis online presence with TTL heartbeats.
- Friends presence API.
- Socket.IO live rooms per match.
- Reconnect endpoint for active matches.
- Live and async turn deadlines.
- Background timeout sweep with server-side forfeit.
- Elo-style skill rating settlement (K=32).
- Rating before/after/delta stored on each match player.
- Multi-device-aware presence disconnect behavior.
- Android deep-link parser for `colonyclash://match/...` and `colonyclash://challenge/...`.
- Android matchmaking UI for Live / Async / Quick Match / Friend Challenge / Reconnect.

## Matchmaking rule
Initial accepted rating gap is ±100. Every 10 seconds in queue it expands by 50 points, capped at ±600. Same-region candidates are preferred; `global` acts as fallback.

## Timeouts
Defaults are environment-controlled:
- Live turn: 45 seconds
- Async turn: 24 hours
- Live match lifetime: 20 minutes
- Async match lifetime: 7 days

A timed-out current player loses by forfeit. Result, reward and rating settlement are server-owned.

## Reconnect
The client can call `GET /api/v1/matchmaking/reconnect` after app restart/network loss. Active non-expired matches are returned in last-activity order.

## Realtime
Socket namespace: `/rt`

Client events:
- `presence.heartbeat`
- `match.join`
- `match.heartbeat`
- `match.event`
- `emote`

Server events:
- `presence.changed`
- `match.presence`
- `match.event`
- `emote`

Authoritative game actions remain on the REST match API; realtime events are transport/presence/UI signals only.
