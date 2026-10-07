# Colony Clash v0.11.0 — Competitive Core

## Product sentence
A premium 60–75 second tactical social competition for students and friend groups.
A player can always play alone, but every battle creates rivalry and later strengthens a real group identity.

## Core gameplay
Three zones: Left / Center / Right.
Seven rounds.
Every round: choose a zone + choose a tactic.

Tactics:
- PUSH: costs 2 energy; beats TRICK
- HOLD: costs 1 energy; beats PUSH
- TRICK: costs 1 energy; beats HOLD

Same-zone encounters resolve by tactic counters.
Split-zone actions add influence independently.
The winner controls more zones; total influence breaks ties.

## AI fairness
Bots read only visible state:
- zone influence
- energy
- recent revealed actions

They never read the player's pending choice.
No hidden bonuses.
Difficulty is fixed before the match from rating proximity.

Personas:
- NOVA: balanced
- RUSH: pressure
- MIRA: defense
- ZERO: prediction/trick
- VEX: resource discipline

Medium AI uses bounded deterministic noise while still choosing sensible moves.

## Navigation
Permanent:
- Home
- Colony
- BATTLE
- Social

Daily is a Home event, not a permanent tab.

## Visual language
Material 3 is infrastructure only.
Visible palette:
Midnight Navy / Lapis / Persian Turquoise / Warm Amber / Rival Coral / restrained Ivory.

No emoji as primary art.
Core brand, navigation and arena marks are built from Compose/Canvas geometry.
External 2.5D art can later replace geometry without changing the information architecture.

## Social
Player:
STRANGER → RIVAL → FAMILIAR_RIVAL → FRIEND_REQUEST → FRIEND

Colony:
NEUTRAL / SAME_COLONY / RIVAL_COLONY

Repeated battles create rivalry.
Friendship remains explicit.

## Colony
A colony uses a real identity:
University / Faculty / Dorm / Class / Friend group / Custom.

Never invent a fake school or colony identity for the player.

## Scope freeze
No Territory, Voice, Spectator, complex Shop, Gift economy, extra currencies or extra mini-games
until the tactical battle passes five-consecutive-match real-device testing.
