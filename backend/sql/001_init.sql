CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username text NOT NULL UNIQUE,
  display_name text NOT NULL,
  birth_year int,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','suspended','deleted')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS colonies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id uuid NOT NULL REFERENCES users(id),
  name text NOT NULL,
  slug text NOT NULL UNIQUE,
  motto text NOT NULL DEFAULT '',
  level int NOT NULL DEFAULT 1 CHECK (level >= 1),
  xp bigint NOT NULL DEFAULT 0 CHECK (xp >= 0),
  member_limit int NOT NULL DEFAULT 30 CHECK (member_limit BETWEEN 2 AND 10000),
  visibility text NOT NULL DEFAULT 'public' CHECK (visibility IN ('public','invite_only','private')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS colony_members (
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role text NOT NULL DEFAULT 'member' CHECK (role IN ('owner','captain','recruiter','member')),
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (colony_id, user_id)
);

CREATE TABLE IF NOT EXISTS friendships (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  friend_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status text NOT NULL CHECK (status IN ('pending','accepted','blocked')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, friend_user_id),
  CHECK (user_id <> friend_user_id)
);

CREATE TABLE IF NOT EXISTS matches (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  game_code text NOT NULL,
  mode text NOT NULL CHECK (mode IN ('live','async')),
  state text NOT NULL DEFAULT 'created' CHECK (state IN ('created','active','finished','cancelled')),
  created_by uuid NOT NULL REFERENCES users(id),
  seed bigint NOT NULL,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS match_players (
  match_id uuid NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  score bigint NOT NULL DEFAULT 0,
  result text CHECK (result IN ('win','loss','draw')),
  PRIMARY KEY (match_id, user_id)
);

CREATE TABLE IF NOT EXISTS wallet_accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_type text NOT NULL CHECK (owner_type IN ('user','colony','system')),
  owner_id uuid,
  currency text NOT NULL CHECK (currency IN ('coin','gem','energy','love','power')),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(owner_type, owner_id, currency)
);

CREATE TABLE IF NOT EXISTS wallet_ledger (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES wallet_accounts(id),
  amount bigint NOT NULL CHECK (amount <> 0),
  reason text NOT NULL,
  reference_type text NOT NULL,
  reference_id text NOT NULL,
  idempotency_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wallet_ledger_account ON wallet_ledger(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS store_items (
  sku text PRIMARY KEY,
  type text NOT NULL CHECK (type IN ('booster','gift','cosmetic','colony_upgrade','pass')),
  title text NOT NULL,
  currency text NOT NULL CHECK (currency IN ('coin','gem')),
  price bigint NOT NULL CHECK (price >= 0),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  active boolean NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS gifts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  sender_user_id uuid NOT NULL REFERENCES users(id),
  receiver_user_id uuid NOT NULL REFERENCES users(id),
  sku text NOT NULL REFERENCES store_items(sku),
  message text NOT NULL DEFAULT '',
  opened_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (sender_user_id <> receiver_user_id)
);

CREATE TABLE IF NOT EXISTS invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  inviter_user_id uuid NOT NULL REFERENCES users(id),
  colony_id uuid REFERENCES colonies(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  selected_contact_label text,
  selected_phone_hash text,
  status text NOT NULL DEFAULT 'created' CHECK (status IN ('created','accepted','expired','revoked')),
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_user_id uuid NOT NULL REFERENCES users(id),
  target_user_id uuid REFERENCES users(id),
  target_type text NOT NULL CHECK (target_type IN ('user','chat','colony','gift_message')),
  target_id text NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewing','resolved','rejected')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_blocks (
  blocker_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(blocker_user_id, blocked_user_id),
  CHECK (blocker_user_id <> blocked_user_id)
);

INSERT INTO store_items(sku,type,title,currency,price,payload) VALUES
('gift_coffee','gift','Coffee','gem',20,'{"energy":20,"love":5}'),
('gift_teddy','gift','Teddy Bear','gem',80,'{"energy":10,"love":30}'),
('gift_bouquet','gift','Bouquet','gem',120,'{"love":50}'),
('boost_extra_life','booster','Extra Life','gem',15,'{"lives":1}'),
('boost_hint','booster','Hint','gem',10,'{"hints":1}'),
('colony_slots_10','colony_upgrade','+10 Member Slots','gem',250,'{"member_slots":10}')
ON CONFLICT (sku) DO NOTHING;
