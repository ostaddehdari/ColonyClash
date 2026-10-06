# Colony Clash v0.8.1 — Stage 07 Season & Territory

Stage 07 adds a persistent seasonal territory layer on top of Stage 06 Colony War.

## New in v0.8.1
- 28-day seasons with automatic rollover
- immutable archived season rank snapshots
- 12-node connected territory map, 3 tiers
- neutral frontier entry claim
- frontier attack entry when the map is full
- adjacency-gated expansion after first territory
- territory shields and contested state
- defender acceptance deadline + forfeit capture
- Stage 06 War-backed territory battles
- server-authoritative capture/defense settlement
- earned fortification I–III
- season leaderboard and colony stats
- territory event history
- Android Territory Map preview
- `colonyclash://territory/{CODE}` deep link
- English, Persian, French, Arabic and Simplified Chinese UI retained

## Season scoring
- participation: +25
- War win: +100
- neutral claim: +75
- territory capture: +250
- successful defense: +150

## Security
The phone never decides territory ownership. Ownership changes only from a locked server transaction after a Stage 06 War result or an expired defense deadline. `season_war_settlements` prevents double settlement and a partial unique index prevents two simultaneous attacks on one territory.

See `docs/STAGE_07.md` and `docs/API_STAGE07.md`.

## v0.8.1 Build Pipeline

This package now includes a reproducible GitHub Actions and Ubuntu build-server pipeline for real debug APKs on every stage. See `BUILD-REAL-APK.md`.
