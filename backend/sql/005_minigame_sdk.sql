ALTER TABLE matches ADD COLUMN IF NOT EXISTS challenge_id uuid REFERENCES challenges(id) ON DELETE SET NULL;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS game_version int NOT NULL DEFAULT 1;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS max_actions int NOT NULL DEFAULT 10 CHECK (max_actions BETWEEN 1 AND 1000);
ALTER TABLE matches ADD COLUMN IF NOT EXISTS current_turn_user_id uuid REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS action_count int NOT NULL DEFAULT 0 CHECK (action_count >= 0);
ALTER TABLE matches ADD COLUMN IF NOT EXISTS game_state jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS result_payload jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reward_settled_at timestamptz;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS rematch_of uuid REFERENCES matches(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS match_actions (
  id bigserial PRIMARY KEY,
  match_id uuid NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  seq int NOT NULL CHECK (seq >= 1),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_action_id text NOT NULL,
  action_type text NOT NULL,
  action_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  state_hash text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(match_id, seq),
  UNIQUE(match_id, user_id, client_action_id)
);
CREATE INDEX IF NOT EXISTS idx_match_actions_match ON match_actions(match_id,seq);

CREATE TABLE IF NOT EXISTS game_catalog (
  game_code text PRIMARY KEY,
  title text NOT NULL,
  version int NOT NULL DEFAULT 1,
  min_players int NOT NULL DEFAULT 2 CHECK (min_players >= 1),
  max_players int NOT NULL DEFAULT 2 CHECK (max_players >= min_players),
  max_actions int NOT NULL DEFAULT 10 CHECK (max_actions >= 1),
  supports_live boolean NOT NULL DEFAULT true,
  supports_async boolean NOT NULL DEFAULT true,
  enabled boolean NOT NULL DEFAULT true,
  config jsonb NOT NULL DEFAULT '{}'::jsonb,
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO game_catalog(game_code,title,version,min_players,max_players,max_actions,supports_live,supports_async,config)
VALUES('color_war_10','Color War 10',1,2,2,10,true,true,'{"boardSize":5,"startingCells":2}'::jsonb)
ON CONFLICT(game_code) DO UPDATE SET title=EXCLUDED.title,version=EXCLUDED.version,max_actions=EXCLUDED.max_actions,config=EXCLUDED.config,updated_at=now();

INSERT INTO game_catalog(game_code,title,version,min_players,max_players,max_actions,supports_live,supports_async,config) VALUES
('number_grab_10','Number Grab 10',1,2,2,10,true,true,'{"boardSize":4,"visibleValues":true}'::jsonb),
('treasure_flip_10','Treasure Flip 10',1,2,2,10,true,true,'{"boardSize":4,"hiddenValues":true}'::jsonb)
ON CONFLICT(game_code) DO UPDATE SET title=EXCLUDED.title,version=EXCLUDED.version,max_actions=EXCLUDED.max_actions,config=EXCLUDED.config,updated_at=now();
