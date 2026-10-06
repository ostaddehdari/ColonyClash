ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at timestamptz;

ALTER TABLE matches ADD COLUMN IF NOT EXISTS matchmaking_ticket_id uuid;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS room_code text;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS region_code text NOT NULL DEFAULT 'global';
ALTER TABLE matches ADD COLUMN IF NOT EXISTS last_activity_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE matches ADD COLUMN IF NOT EXISTS turn_deadline_at timestamptz;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS expires_at timestamptz;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS timeout_reason text;
CREATE UNIQUE INDEX IF NOT EXISTS idx_matches_room_code ON matches(room_code) WHERE room_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_matches_active_deadline ON matches(state,turn_deadline_at) WHERE state='active';
CREATE INDEX IF NOT EXISTS idx_matches_active_player_reconnect ON matches(state,last_activity_at DESC);

ALTER TABLE match_players ADD COLUMN IF NOT EXISTS rating_before int NOT NULL DEFAULT 1000;
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS rating_after int;
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS rating_delta int;
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS connected_at timestamptz;
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS disconnected_at timestamptz;

CREATE TABLE IF NOT EXISTS matchmaking_tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  game_code text NOT NULL REFERENCES game_catalog(game_code),
  mode text NOT NULL CHECK (mode IN ('live','async')),
  region_code text NOT NULL DEFAULT 'global',
  skill_rating int NOT NULL DEFAULT 1000 CHECK (skill_rating >= 0),
  status text NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','matching','matched','cancelled','expired')),
  match_id uuid REFERENCES matches(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  matched_at timestamptz,
  expires_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_matchmaking_queue ON matchmaking_tickets(game_code,mode,region_code,status,created_at);
CREATE INDEX IF NOT EXISTS idx_matchmaking_user ON matchmaking_tickets(user_id,status,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_matchmaking_one_live_ticket
  ON matchmaking_tickets(user_id,game_code,mode)
  WHERE status IN ('queued','matching');

CREATE TABLE IF NOT EXISTS match_invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  invited_user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  game_code text NOT NULL REFERENCES game_catalog(game_code),
  mode text NOT NULL CHECK (mode IN ('live','async')),
  public_code text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','accepted','cancelled','expired')),
  match_id uuid REFERENCES matches(id) ON DELETE SET NULL,
  message text NOT NULL DEFAULT '',
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_match_invites_user ON match_invites(invited_user_id,status,expires_at);

ALTER TABLE challenges ADD COLUMN IF NOT EXISTS match_id uuid REFERENCES matches(id) ON DELETE SET NULL;
