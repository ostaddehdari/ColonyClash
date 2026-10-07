# Colony Clash v0.9.0 — UX + Relationship Architecture

## Core product rule
A player can always play. Friends, clans and social graph add depth; they never gate the primary loop.

## Primary loop
Launch -> Home -> Battle -> human search -> bot fallback -> result -> reward -> rematch / rival -> progression.

## Navigation
Primary: Home / Colony / Battle / Armory / Social.
Secondary surfaces: Shop, League, Profile, Season, Events.

Battle is the visual center of the bottom navigation and must be reachable with one tap from every primary screen.

## Relationship graph
Player relationship is directional and independent from moderation and group membership.

Player relationship:
- STRANGER
- RIVAL
- FRIEND_REQUEST_SENT
- FRIEND_REQUEST_RECEIVED
- FRIEND

Independent group axes:
- Colony: NONE / SAME_COLONY / ALLY_COLONY / ENEMY_COLONY
- Squad: NONE / SAME_SQUAD

Independent safety flags:
- blockedByMe
- mutedByMe
- reportedByMe

Battle history:
- battles
- myWins
- theirWins
- rivalryPoints

Rules:
1. First completed battle may promote STRANGER -> RIVAL.
2. A battle never auto-creates FRIEND.
3. Friend request must be explicit and directional.
4. Accepting an incoming request creates FRIEND.
5. Blocking never changes colony/squad history; it only suppresses interaction surfaces.
6. Colony enemy/ally state does not overwrite player friendship.
7. A player can be both FRIEND and ENEMY_COLONY.
8. A squadmate may also be a RIVAL in ranked history, but Social UI prioritizes squad/friend context.

## Invite UX
No raw custom URI is shown to the player.

Primary flow:
- enter phone or pick contact
- open system SMS composer with signed short link
- or use Android Sharesheet

Future production links:
- https://cc.srun.ir/i/<token> game invite
- https://cc.srun.ir/c/<token> colony invite
- https://cc.srun.ir/w/<token> war invite
- https://cc.srun.ir/t/<token> territory invite

No READ_CONTACTS and no SEND_SMS permission are required.

## Visual language
Material 3 is infrastructure, not the visible identity.

Game skin:
- deep navy base
- blue/purple layered surfaces
- lime/yellow Battle CTA
- gold economy/reward accents
- thick 18–28dp rounded cards
- oversized numbers and rewards
- center-weighted action hierarchy
- isometric 2.5D illustration slots for Home / Colony / Season
- short local animations only on first paint

## Loading target
- zero network dependency before first playable UI
- local-only splash
- target visible duration ~320 ms
- network sync starts after Home is interactive
