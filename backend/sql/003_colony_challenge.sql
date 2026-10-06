ALTER TABLE colonies ADD COLUMN IF NOT EXISTS description text NOT NULL DEFAULT '';
ALTER TABLE colonies ADD COLUMN IF NOT EXISTS emblem_key text;
ALTER TABLE colonies ADD COLUMN IF NOT EXISTS banner_key text;
ALTER TABLE colonies ADD COLUMN IF NOT EXISTS region_code text;
ALTER TABLE colonies ADD COLUMN IF NOT EXISTS war_points bigint NOT NULL DEFAULT 0 CHECK (war_points >= 0);
ALTER TABLE colonies ADD COLUMN IF NOT EXISTS season_points bigint NOT NULL DEFAULT 0 CHECK (season_points >= 0);

ALTER TABLE colony_members DROP CONSTRAINT IF EXISTS colony_members_role_check;
ALTER TABLE colony_members ADD CONSTRAINT colony_members_role_check
  CHECK (role IN ('owner','captain','recruiter','defender','raider','member'));

CREATE TABLE IF NOT EXISTS colony_invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  created_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  invited_user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','accepted','revoked','expired')),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_colony_invites_colony ON colony_invites(colony_id,status,expires_at);
CREATE INDEX IF NOT EXISTS idx_colony_invites_invited ON colony_invites(invited_user_id,status,expires_at);

CREATE TABLE IF NOT EXISTS player_stats (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  xp bigint NOT NULL DEFAULT 0 CHECK (xp >= 0),
  level int NOT NULL DEFAULT 1 CHECK (level >= 1),
  wins bigint NOT NULL DEFAULT 0 CHECK (wins >= 0),
  losses bigint NOT NULL DEFAULT 0 CHECK (losses >= 0),
  draws bigint NOT NULL DEFAULT 0 CHECK (draws >= 0),
  win_streak int NOT NULL DEFAULT 0 CHECK (win_streak >= 0),
  best_win_streak int NOT NULL DEFAULT 0 CHECK (best_win_streak >= 0),
  skill_rating int NOT NULL DEFAULT 1000 CHECK (skill_rating >= 0),
  recruiter_points bigint NOT NULL DEFAULT 0 CHECK (recruiter_points >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO player_stats(user_id) SELECT id FROM users ON CONFLICT DO NOTHING;

ALTER TABLE challenges ADD COLUMN IF NOT EXISTS public_code text;
ALTER TABLE challenges ADD COLUMN IF NOT EXISTS accepted_by uuid REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE challenges ADD COLUMN IF NOT EXISTS accepted_at timestamptz;
ALTER TABLE challenges ADD COLUMN IF NOT EXISTS share_count int NOT NULL DEFAULT 0 CHECK (share_count >= 0);
CREATE UNIQUE INDEX IF NOT EXISTS idx_challenges_public_code ON challenges(public_code) WHERE public_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS challenge_events (
  id bigserial PRIMARY KEY,
  challenge_id uuid NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  event_type text NOT NULL CHECK (event_type IN ('created','shared','accepted','declined','cancelled','started','finished')),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_challenge_events_challenge ON challenge_events(challenge_id,created_at);

CREATE TABLE IF NOT EXISTS squad_invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  squad_id uuid NOT NULL REFERENCES squads(id) ON DELETE CASCADE,
  created_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  invited_user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','accepted','revoked','expired')),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_squad_invites_squad ON squad_invites(squad_id,status,expires_at);
