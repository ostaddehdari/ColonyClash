# Stage 02 — Colony, Rivalry and Challenge Loop

Version: `0.3.0`

## Goal
Turn the Stage 01 social foundation into the first complete social competition loop:

`Profile → Friend/Squad/Colony → Invite → Rival → Challenge → Accept → Match handoff`

## Colony capabilities
- Create public / invite-only / private colony
- Roles: owner, captain, recruiter, defender, raider, member
- Member list with public skill summary
- Role changes with permission boundaries
- Remove / leave rules
- Time-limited invite token
- Deep-link and QR payload for colony invites
- Capacity enforcement under a database row lock
- Colony war points and season points fields

## Squad capabilities
- Create squad
- List the player's squads
- Member list
- Owner/captain invite links
- Time-limited invite acceptance
- Capacity enforcement

## Rivalry
- Player-vs-player rivalry
- Squad or colony rivalry creation by authorized leaders
- Win/loss/draw counters retained in the generic rivalry model

## Challenge flow
- Live or async challenge
- Target: user / squad / colony / open
- Public challenge code
- Share URL and app deep link
- Incoming challenge feed
- Server-side target membership validation
- Accept/cancel/share events
- Challenge event audit trail

## Android UI
- Four-tab lightweight Compose shell: Home, Colony, Challenge, Profile
- Colony summary + role preview
- Challenge card and Android Sharesheet
- Contact picker remains single-contact only
- QR generation is local using ZXing Core; QR payload contains only an invite/deep link
- No broad contacts permission

## Not yet part of Stage 02
- Production identity provider / OTP
- Match engine and deterministic gameplay protocol
- Store checkout / Google Play Billing settlement
- Push notifications
- Production domain and Universal/App Links verification
