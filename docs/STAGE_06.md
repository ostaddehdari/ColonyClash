# Stage 06 — Colony War

This stage turns colony rivalry into a live, multi-round social event.

Features:
- Colony vs Colony declaration and acceptance
- Command roles: owner/captain
- Fighter roster selection
- 1–9 rounds (default 5)
- Automatic rotation across the Mini Game SDK catalog
- Live/async war modes
- Spectator room and persisted/rate-limited emotes
- Server-authoritative live score
- MVP leaderboard
- Colony War Points and Season Points
- Revenge/rematch flow

Round pipeline:
`War → Roster → Round → Match Engine → Match Result → War Settlement → Next Round → MVP → War Reward → Rematch`
