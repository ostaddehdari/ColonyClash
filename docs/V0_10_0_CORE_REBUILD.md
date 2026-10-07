# Colony Clash v0.10.0 — Core Rebuild

## Product sentence
A 60–90 second social competitive game for student/friend groups. A player can always play alone, but every win strengthens their group identity.

## Scope lock
Only:
1. Home
2. Battle / Colony Rush
3. Bot fallback
4. Result + reward + one-tap rematch
5. Daily 3-win loop
6. 24-hour colony rivalry
7. Phone/SMS/social invite
8. Player relationship domain
9. Local persistent progression

Frozen for this release:
- Territory
- Voice
- Spectator
- complex Shop
- Gift economy
- multi-currency
- heavy clan governance
- extra mini-games

## Core loop
Open → Battle → rival search → NOVA fallback → 5 gate decisions → result → reward → rematch.

## Social model
Player:
STRANGER → RIVAL → REPEATED_RIVAL → FRIEND_REQUEST → FRIEND

Colony:
NEUTRAL / SAME_COLONY / RIVAL_COLONY

Safety flags remain separate:
block / mute / report.

## Progression
Visible:
- Level
- Trophy
- Coin
- Streak

Battle rewards:
- Win: +28 trophy, +20 XP, +100 coin, +3 colony points
- Loss: -8 trophy, +8 XP, +40 coin, +1 colony point
- Draw: +4 trophy

## Daily
3 wins → 250 coins.
Daily state resets by local date.

## KPI gate
- Time to first battle < 20s
- Match start > 80%
- Match completion > 85%
- Rematch > 30%
- D1 > 30%
- Matches / DAU > 5
- Colony join > 25%
- Invite send > 10%

No new major feature enters the build until the core loop is fun in repeated real-device testing.
