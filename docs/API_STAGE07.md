# Stage 07 API

All endpoints require the normal JWT guard.

## Seasons
- `GET /seasons/active` — active season, time remaining and caller colony rank when available.
- `GET /seasons/history` — recent active/archived seasons.
- `GET /seasons/:id/leaderboard` — current dynamic ranking or archived immutable snapshot.

## Territory
- `GET /territories/map` — nodes, virtual coordinates, tier, owner, state, shield, fortification, adjacency.
- `GET /territories/battles/mine` — territory battles involving any of the caller's colonies.
- `GET /territories/:code/history` — capture/defense history.
- `POST /territories/sync` — reconcile expired defense windows and return current map.
- `POST /territories/:code/claim` body `{ "colonyId": "uuid" }` — one neutral Tier-1 entry claim.
- `POST /territories/:code/challenge` body `{ "attackerColonyId":"uuid", "mode":"live|async", "roundCount":5 }` — reserve node and create a real Stage 06 Colony War.

## Territory challenge settlement
The client never submits a capture result. `WarsService` reads the final server War result and writes exactly one `season_war_settlements` row.

- attacker wins → owner changes to attacker, +250 capture points, 45-minute shield.
- defender wins or War draws → defender keeps node, +150 defense points, fortification +1 (max III), shield increases with fortification.
- defender does not accept before defense deadline → attacker capture by forfeit.

## Deep link
`colonyclash://territory/{TERRITORY_CODE}`
