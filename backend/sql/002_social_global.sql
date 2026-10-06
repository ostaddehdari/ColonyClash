ALTER TABLE users ADD COLUMN IF NOT EXISTS locale text NOT NULL DEFAULT 'en';
ALTER TABLE users ADD COLUMN IF NOT EXISTS country_code text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code text;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_key text;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_referral_code ON users(referral_code) WHERE referral_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS squads (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name text NOT NULL,
  slug text NOT NULL UNIQUE,
  member_limit int NOT NULL DEFAULT 8 CHECK (member_limit BETWEEN 2 AND 24),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS squad_members (
  squad_id uuid NOT NULL REFERENCES squads(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role text NOT NULL DEFAULT 'member' CHECK (role IN ('owner','captain','member')),
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (squad_id, user_id)
);

CREATE TABLE IF NOT EXISTS rivalries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  challenger_type text NOT NULL CHECK (challenger_type IN ('user','squad','colony')),
  challenger_id uuid NOT NULL,
  opponent_type text NOT NULL CHECK (opponent_type IN ('user','squad','colony')),
  opponent_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','muted','ended')),
  wins bigint NOT NULL DEFAULT 0,
  losses bigint NOT NULL DEFAULT 0,
  draws bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (challenger_id <> opponent_id OR challenger_type <> opponent_type)
);
CREATE INDEX IF NOT EXISTS idx_rivalries_challenger ON rivalries(challenger_type, challenger_id);
CREATE INDEX IF NOT EXISTS idx_rivalries_opponent ON rivalries(opponent_type, opponent_id);

CREATE TABLE IF NOT EXISTS challenges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type text NOT NULL CHECK (target_type IN ('user','squad','colony','open')),
  target_id uuid,
  game_code text NOT NULL,
  mode text NOT NULL DEFAULT 'live' CHECK (mode IN ('live','async')),
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','accepted','declined','expired','cancelled','started','finished')),
  message text NOT NULL DEFAULT '',
  stake_points int NOT NULL DEFAULT 0 CHECK (stake_points BETWEEN 0 AND 1000000),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_challenges_target ON challenges(target_type, target_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS price_catalog (
  sku text NOT NULL REFERENCES store_items(sku) ON DELETE CASCADE,
  market text NOT NULL CHECK (market IN ('play_global','direct_iran','direct_china')),
  currency text NOT NULL,
  amount_minor bigint NOT NULL CHECK (amount_minor >= 0),
  provider text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  PRIMARY KEY (sku, market, currency)
);

INSERT INTO price_catalog(sku,market,currency,amount_minor,provider) VALUES
('gift_coffee','play_global','USD',99,'google_play'),
('gift_teddy','play_global','USD',299,'google_play'),
('gift_bouquet','play_global','USD',399,'google_play'),
('boost_extra_life','play_global','USD',99,'google_play'),
('boost_hint','play_global','USD',49,'google_play'),
('colony_slots_10','play_global','USD',499,'google_play'),
('gift_coffee','direct_iran','IRT',70000,'iran_gateway'),
('gift_teddy','direct_iran','IRT',180000,'iran_gateway'),
('gift_bouquet','direct_iran','IRT',250000,'iran_gateway'),
('boost_extra_life','direct_iran','IRT',60000,'iran_gateway'),
('boost_hint','direct_iran','IRT',35000,'iran_gateway'),
('colony_slots_10','direct_iran','IRT',350000,'iran_gateway')
ON CONFLICT DO NOTHING;
