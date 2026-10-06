# Stage 07 — Season & Territory

Stage 07 turns Colony War into a persistent seasonal conquest loop.

## Core loop
`Enter season → claim/attack frontier → own territory → attack adjacent node → defender accepts before deadline → Stage 06 War → server settles ownership → shield/fortification → leaderboard → season archive/reset`

## Territory map
The initial map contains 12 connected abstract territories in three tiers. Coordinates are data, not GPS. No precise location is required.

### Entry rules
- A colony with zero territory may claim one neutral Tier-1 frontier node once per season.
- If no neutral frontier is left, a colony with zero territory may challenge an owned Tier-1 frontier node.
- Once a colony owns territory, it may only challenge adjacent territory.
- Shielded or already-contested territory cannot be challenged.

## Defense
- Live territory challenge acceptance deadline: 30 minutes.
- Async territory challenge acceptance deadline: 12 hours.
- If the defender never accepts before the deadline, the attacker captures by forfeit.
- A defender win **or draw** holds the territory.
- Successful defense increases earned fortification up to III and extends the post-defense shield.
- Capture resets fortification to 0.
- Fortification never changes Mini Game score, so the battle remains skill-based.

## Season scoring
- War participation: +25 per colony.
- War win: +100.
- Neutral frontier claim: +75.
- Territory capture: +250.
- Successful territory defense: +150.

Season ranking uses `season_colony_stats`, not client values and not the legacy `colonies.season_points` field.

## Season lifecycle
- One active season at a time.
- Default season duration: 28 days.
- Rollover is protected by a PostgreSQL advisory transaction lock.
- On expiry, final ranking is written to `season_rank_snapshots` before archival.
- A new season is created automatically and all map ownership starts neutral again.

## Idempotency / concurrency
- `season_war_settlements.war_id` is unique: one seasonal settlement per War.
- One open battle per territory is enforced with a partial unique index.
- Territory ownership rows are locked before claim/challenge/forfeit settlement.
- Stage 06 War remains authoritative for battle results.

## Privacy
Territory map coordinates are virtual UI coordinates only. This stage does not require or persist device GPS or precise school/university location.
