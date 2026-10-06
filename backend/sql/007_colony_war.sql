CREATE TABLE IF NOT EXISTS colony_wars (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  challenger_colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  defender_colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  created_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  accepted_by uuid REFERENCES users(id) ON DELETE SET NULL,
  public_code text NOT NULL UNIQUE,
  mode text NOT NULL DEFAULT 'live' CHECK (mode IN ('live','async')),
  status text NOT NULL DEFAULT 'declared' CHECK (status IN ('declared','accepted','active','finished','cancelled','expired')),
  round_count int NOT NULL DEFAULT 5 CHECK (round_count BETWEEN 1 AND 9),
  current_round int NOT NULL DEFAULT 0 CHECK (current_round >= 0),
  challenger_score int NOT NULL DEFAULT 0 CHECK (challenger_score >= 0),
  defender_score int NOT NULL DEFAULT 0 CHECK (defender_score >= 0),
  winner_colony_id uuid REFERENCES colonies(id) ON DELETE SET NULL,
  starts_at timestamptz,
  accepted_at timestamptz,
  started_at timestamptz,
  finished_at timestamptz,
  expires_at timestamptz NOT NULL DEFAULT (now()+interval '24 hours'),
  rematch_of uuid REFERENCES colony_wars(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (challenger_colony_id <> defender_colony_id)
);
CREATE INDEX IF NOT EXISTS idx_colony_wars_status ON colony_wars(status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_colony_wars_challenger ON colony_wars(challenger_colony_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_colony_wars_defender ON colony_wars(defender_colony_id,status,created_at DESC);

CREATE TABLE IF NOT EXISTS colony_war_roster (
  war_id uuid NOT NULL REFERENCES colony_wars(id) ON DELETE CASCADE,
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  slot int NOT NULL CHECK (slot BETWEEN 1 AND 9),
  added_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (war_id,colony_id,slot),
  UNIQUE (war_id,colony_id,user_id)
);
CREATE INDEX IF NOT EXISTS idx_colony_war_roster_user ON colony_war_roster(user_id,war_id);

CREATE TABLE IF NOT EXISTS colony_war_rounds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  war_id uuid NOT NULL REFERENCES colony_wars(id) ON DELETE CASCADE,
  round_no int NOT NULL CHECK (round_no BETWEEN 1 AND 9),
  game_code text NOT NULL REFERENCES game_catalog(game_code),
  challenger_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  defender_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  match_id uuid REFERENCES matches(id) ON DELETE SET NULL,
  state text NOT NULL DEFAULT 'pending' CHECK (state IN ('pending','active','finished','cancelled')),
  challenger_points int NOT NULL DEFAULT 0 CHECK (challenger_points >= 0),
  defender_points int NOT NULL DEFAULT 0 CHECK (defender_points >= 0),
  winner_colony_id uuid REFERENCES colonies(id) ON DELETE SET NULL,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(war_id,round_no)
);
CREATE INDEX IF NOT EXISTS idx_colony_war_rounds_match ON colony_war_rounds(match_id) WHERE match_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS colony_war_spectators (
  war_id uuid NOT NULL REFERENCES colony_wars(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(war_id,user_id)
);

CREATE TABLE IF NOT EXISTS colony_war_emotes (
  id bigserial PRIMARY KEY,
  war_id uuid NOT NULL REFERENCES colony_wars(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_colony_war_emotes_recent ON colony_war_emotes(war_id,created_at DESC);

CREATE TABLE IF NOT EXISTS colony_war_player_stats (
  war_id uuid NOT NULL REFERENCES colony_wars(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  rounds_played int NOT NULL DEFAULT 0 CHECK (rounds_played >= 0),
  rounds_won int NOT NULL DEFAULT 0 CHECK (rounds_won >= 0),
  points int NOT NULL DEFAULT 0 CHECK (points >= 0),
  mvp_score int NOT NULL DEFAULT 0 CHECK (mvp_score >= 0),
  PRIMARY KEY(war_id,user_id)
);
