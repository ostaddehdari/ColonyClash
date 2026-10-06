-- Stage 03: Economy & Shop

-- More store metadata without breaking the Stage 00 catalog.
ALTER TABLE store_items ADD COLUMN IF NOT EXISTS description text NOT NULL DEFAULT '';
ALTER TABLE store_items ADD COLUMN IF NOT EXISTS icon_key text;
ALTER TABLE store_items ADD COLUMN IF NOT EXISTS sort_order int NOT NULL DEFAULT 100;
ALTER TABLE store_items ADD COLUMN IF NOT EXISTS max_stack int NOT NULL DEFAULT 9999 CHECK (max_stack BETWEEN 1 AND 1000000);
ALTER TABLE store_items ADD COLUMN IF NOT EXISTS purchase_limit_per_day int;
ALTER TABLE store_items ADD COLUMN IF NOT EXISTS tradable boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS inventory_items (
  owner_type text NOT NULL CHECK (owner_type IN ('user','colony')),
  owner_id uuid NOT NULL,
  sku text NOT NULL REFERENCES store_items(sku) ON DELETE RESTRICT,
  quantity bigint NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(owner_type, owner_id, sku)
);
CREATE INDEX IF NOT EXISTS idx_inventory_owner ON inventory_items(owner_type, owner_id);

CREATE TABLE IF NOT EXISTS inventory_ledger (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_type text NOT NULL CHECK (owner_type IN ('user','colony')),
  owner_id uuid NOT NULL,
  sku text NOT NULL REFERENCES store_items(sku) ON DELETE RESTRICT,
  delta bigint NOT NULL CHECK (delta <> 0),
  reason text NOT NULL,
  reference_type text NOT NULL,
  reference_id text NOT NULL,
  idempotency_key text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_inventory_ledger_owner ON inventory_ledger(owner_type, owner_id, created_at DESC);

CREATE TABLE IF NOT EXISTS store_orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sku text NOT NULL REFERENCES store_items(sku) ON DELETE RESTRICT,
  quantity int NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 100),
  unit_price bigint NOT NULL CHECK (unit_price >= 0),
  virtual_currency text NOT NULL CHECK (virtual_currency IN ('coin','gem')),
  target_type text CHECK (target_type IN ('user','colony')),
  target_id uuid,
  status text NOT NULL DEFAULT 'completed' CHECK (status IN ('created','completed','cancelled','refunded')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_store_orders_user ON store_orders(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS gift_claims (
  gift_id uuid PRIMARY KEY REFERENCES gifts(id) ON DELETE CASCADE,
  receiver_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rewards jsonb NOT NULL DEFAULT '{}'::jsonb,
  claimed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS colony_upgrades (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  purchased_by uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  sku text NOT NULL REFERENCES store_items(sku),
  effect jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_colony_upgrades_colony ON colony_upgrades(colony_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_entitlements (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  entitlement_key text NOT NULL,
  source_sku text REFERENCES store_items(sku),
  expires_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, entitlement_key)
);

-- Provider receipts are stored, but Stage 03 never trusts them enough to grant value.
-- Verification/granting belongs to the dedicated Billing stage.
CREATE TABLE IF NOT EXISTS purchase_receipts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  market text NOT NULL CHECK (market IN ('play_global','direct_iran','direct_china')),
  provider text NOT NULL,
  product_key text NOT NULL,
  provider_order_id text,
  purchase_token_hash text,
  state text NOT NULL DEFAULT 'received' CHECK (state IN ('received','verified','granted','rejected','refunded')),
  raw_meta jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  verified_at timestamptz,
  granted_at timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchase_receipt_provider_order ON purchase_receipts(provider, provider_order_id) WHERE provider_order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_purchase_receipt_token_hash ON purchase_receipts(provider, purchase_token_hash) WHERE purchase_token_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS billing_products (
  product_key text NOT NULL,
  market text NOT NULL CHECK (market IN ('play_global','direct_iran','direct_china')),
  provider text NOT NULL,
  provider_product_id text NOT NULL,
  product_type text NOT NULL CHECK (product_type IN ('consumable','non_consumable','subscription')),
  grant_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  active boolean NOT NULL DEFAULT true,
  PRIMARY KEY(product_key, market),
  UNIQUE(market, provider_product_id)
);

-- Expanded virtual catalog. Real money only buys provider products; these remain virtual-currency items.
INSERT INTO store_items(sku,type,title,currency,price,payload,description,icon_key,sort_order,max_stack) VALUES
('boost_shield','booster','Shield','gem',20,'{"shield":1}','Blocks one eligible hit','shield',21,99),
('boost_retry','booster','Retry','gem',25,'{"retry":1}','Retry one eligible failed action','retry',22,99),
('boost_time_3','booster','+3 Seconds','gem',15,'{"time_seconds":3}','Adds three seconds where the game permits it','timer',23,99),
('boost_rage','booster','Rage','gem',30,'{"rage":1}','One tactical power action','rage',24,99),
('boost_focus','booster','Focus','gem',20,'{"focus":1}','Aiming/focus helper for supported games','focus',25,99),
('boost_xp2','booster','2x XP','gem',35,'{"xp_multiplier":2,"uses":1}','Double eligible XP for one result','xp2',26,99),
('gift_rose','gift','Rose','gem',15,'{"love":8}','A small social gift','rose',40,1),
('gift_cake','gift','Cake','gem',60,'{"energy":15,"love":20}','Celebration gift','cake',41,1),
('gift_crown','gift','Crown','gem',500,'{"love":100,"power":10}','Premium visible social gift','crown',42,1),
('gift_castle','gift','Castle','gem',5000,'{"love":500,"power":50}','High-status animated social gift','castle',43,1),
('colony_slots_25','colony_upgrade','+25 Member Slots','gem',550,'{"member_slots":25}','Increase colony capacity','members25',60,1),
('colony_banner_fx','colony_upgrade','Animated Colony Banner','gem',400,'{"entitlement":"colony_banner_fx"}','Unlock animated colony banner slot','bannerfx',61,1),
('cosmetic_fire_frame','cosmetic','Fire Profile Frame','gem',180,'{"entitlement":"profile_frame_fire"}','Animated profile frame','fireframe',70,1),
('pass_season_basic','pass','Season Premium Pass','gem',900,'{"entitlement":"season_premium"}','Premium season reward track','pass',80,1)
ON CONFLICT (sku) DO UPDATE SET
  title=EXCLUDED.title, price=EXCLUDED.price, payload=EXCLUDED.payload, description=EXCLUDED.description,
  icon_key=EXCLUDED.icon_key, sort_order=EXCLUDED.sort_order, max_stack=EXCLUDED.max_stack, active=true;

-- Ensure original rows also get sane metadata.
UPDATE store_items SET description='Energy and friendship gift',icon_key='coffee',sort_order=30,max_stack=1 WHERE sku='gift_coffee';
UPDATE store_items SET description='Warm friendship gift',icon_key='teddy',sort_order=31,max_stack=1 WHERE sku='gift_teddy';
UPDATE store_items SET description='Large love gift',icon_key='bouquet',sort_order=32,max_stack=1 WHERE sku='gift_bouquet';
UPDATE store_items SET description='One extra eligible life',icon_key='heart',sort_order=10,max_stack=99 WHERE sku='boost_extra_life';
UPDATE store_items SET description='One eligible hint',icon_key='hint',sort_order=11,max_stack=99 WHERE sku='boost_hint';
UPDATE store_items SET description='Increase colony capacity',icon_key='members10',sort_order=50,max_stack=1 WHERE sku='colony_slots_10';

-- Provider product IDs are stable aliases. Prices for Google Play are obtained from ProductDetails at runtime.
INSERT INTO billing_products(product_key,market,provider,provider_product_id,product_type,grant_payload) VALUES
('gems_100','play_global','google_play','gems_100','consumable','{"gem":100}'),
('gems_550','play_global','google_play','gems_550','consumable','{"gem":550}'),
('gems_1200','play_global','google_play','gems_1200','consumable','{"gem":1200}'),
('gems_100','direct_iran','iran_gateway','gems_100','consumable','{"gem":100}'),
('gems_550','direct_iran','iran_gateway','gems_550','consumable','{"gem":550}'),
('gems_1200','direct_iran','iran_gateway','gems_1200','consumable','{"gem":1200}'),
('gems_100','direct_china','china_store','gems_100','consumable','{"gem":100}'),
('gems_550','direct_china','china_store','gems_550','consumable','{"gem":550}'),
('gems_1200','direct_china','china_store','gems_1200','consumable','{"gem":1200}')
ON CONFLICT (product_key,market) DO UPDATE SET provider=EXCLUDED.provider,provider_product_id=EXCLUDED.provider_product_id,grant_payload=EXCLUDED.grant_payload,active=true;
