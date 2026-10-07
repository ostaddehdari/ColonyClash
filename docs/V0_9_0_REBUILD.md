# Colony Clash v0.9.0 — Game Rebuild

## Product rule
The game must never depend on having a friend before it becomes playable.

Primary loop:

Home -> Quick battle -> Human matchmaking -> Bot fallback -> Battle -> Reward -> Rematch / Rival -> Progression

## Social graph
Player relationship and colony relationship are separate dimensions.

Player relationship:
- STRANGER
- RIVAL
- FRIEND_REQUEST_SENT
- FRIEND_REQUEST_RECEIVED
- FRIEND

Independent social flags:
- blockedByMe
- mutedByMe
- reportedByMe

Additional independent relation axes:
- Colony: NONE / SAME_COLONY / ALLY_COLONY / ENEMY_COLONY
- Squad: NONE / SAME_SQUAD

Colony relationship:
- NONE
- SAME_COLONY
- ALLY_COLONY
- ENEMY_COLONY

A completed first battle can turn a STRANGER into a RIVAL. Friend requests are directional (sent/received), and acceptance is explicit. Blocking, muting, reporting, colony relation and squad relation do not overwrite the player relationship.

## Invitation UX
Raw `colonyclash://` links are no longer shown to the player.

User-facing links:
- Game invite: `https://cc.srun.ir/i/<token>`
- Colony invite: `https://cc.srun.ir/c/<token>`
- War invite: `https://cc.srun.ir/w/<token>`
- Territory: `https://cc.srun.ir/t/<token>`

The app keeps the custom URI scheme for compatibility.

Invite UX:
1. Enter phone number or use Android Contact Picker.
2. Open the device SMS composer with the invite body.
3. Or use Android Sharesheet for WhatsApp, Telegram and other installed apps.
4. Backend attribution and signed short-link generation are the next server integration step.

No `READ_CONTACTS` or `SEND_SMS` permission is required for this UX.

## Visual system
Material 3 remains the accessibility/layout foundation only.
The visible skin is game-specific:
- deep navy background
- electric blue/purple surfaces
- lime/yellow primary battle CTA
- gold reward/economy accents
- thick rounded cards
- large visual hierarchy
- center-weighted battle navigation
- isometric/2.5D art slots for Home, Colony and Season

## Loading
The in-app splash is local-only and intentionally short (~320 ms).
No network request may block first playable UI.

## Navigation
Primary bottom navigation:
- Home
- Colony
- Battle (large central CTA)
- Armory
- Social

Shop is opened from currency HUD / home tile.
League is opened from season/progression surfaces.
Profile is opened from the player HUD.

## v0.9.0 technical scope
- New Activity shell separated from game UI.
- Social relation model added.
- Bot controller added for Color War.
- Opponent-first matchmaking UX.
- Human-first queue with immediate bot fallback preview.
- Direct SMS / Contact Picker / Android Sharesheet invitation flow.
- HTTPS invite parsing for `cc.srun.ir`.
- Rebuilt Home, Battle, Colony, Armory, Social, Shop, League and Profile screens.
- Android versionCode 90 / versionName 0.9.0.

## Next after device approval
1. Move bot opponent creation and rewards to backend authority.
2. Persist XP / coins / rating / relationship transitions.
3. Create signed invite tokens and attribution endpoints.
4. Add verified Android App Links (`assetlinks.json`).
5. Replace placeholder emoji art with the final game icon / 2.5D asset pack.
6. Restore full localization for every new v0.9.0 string in EN/FA/FR/AR/ZH-CN.
