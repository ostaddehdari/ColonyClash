CREATE TABLE IF NOT EXISTS seasons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE,
  title_i18n jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'upcoming' CHECK (status IN ('upcoming','active','archived')),
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  archived_at timestamptz,
  CHECK (ends_at > starts_at)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_season ON seasons(status) WHERE status='active';
CREATE INDEX IF NOT EXISTS idx_seasons_window ON seasons(status,starts_at,ends_at);

CREATE TABLE IF NOT EXISTS territory_nodes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code text NOT NULL UNIQUE,
  name_i18n jsonb NOT NULL DEFAULT '{}'::jsonb,
  tier int NOT NULL DEFAULT 1 CHECK (tier BETWEEN 1 AND 3),
  map_x int NOT NULL CHECK (map_x BETWEEN 0 AND 100),
  map_y int NOT NULL CHECK (map_y BETWEEN 0 AND 100),
  score_value int NOT NULL DEFAULT 100 CHECK (score_value > 0),
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS territory_edges (
  from_territory_id uuid NOT NULL REFERENCES territory_nodes(id) ON DELETE CASCADE,
  to_territory_id uuid NOT NULL REFERENCES territory_nodes(id) ON DELETE CASCADE,
  PRIMARY KEY(from_territory_id,to_territory_id),
  CHECK (from_territory_id <> to_territory_id)
);

CREATE TABLE IF NOT EXISTS season_territories (
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  territory_id uuid NOT NULL REFERENCES territory_nodes(id) ON DELETE CASCADE,
  owner_colony_id uuid REFERENCES colonies(id) ON DELETE SET NULL,
  state text NOT NULL DEFAULT 'neutral' CHECK (state IN ('neutral','owned','contested')),
  fortification int NOT NULL DEFAULT 0 CHECK (fortification BETWEEN 0 AND 3),
  defense_streak int NOT NULL DEFAULT 0 CHECK (defense_streak >= 0),
  shield_until timestamptz,
  active_battle_id uuid,
  captured_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(season_id,territory_id)
);
CREATE INDEX IF NOT EXISTS idx_season_territories_owner ON season_territories(season_id,owner_colony_id);
CREATE INDEX IF NOT EXISTS idx_season_territories_state ON season_territories(season_id,state);

CREATE TABLE IF NOT EXISTS territory_battles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  territory_id uuid NOT NULL REFERENCES territory_nodes(id) ON DELETE CASCADE,
  attacker_colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  defender_colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  war_id uuid UNIQUE REFERENCES colony_wars(id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'reserving' CHECK (status IN ('reserving','declared','active','settled','forfeit','cancelled')),
  defense_deadline timestamptz NOT NULL,
  winner_colony_id uuid REFERENCES colonies(id) ON DELETE SET NULL,
  settlement_type text CHECK (settlement_type IN ('capture','defense','forfeit','cancelled')),
  created_at timestamptz NOT NULL DEFAULT now(),
  settled_at timestamptz,
  CHECK (attacker_colony_id <> defender_colony_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_open_territory_battle
  ON territory_battles(season_id,territory_id)
  WHERE status IN ('reserving','declared','active');
CREATE INDEX IF NOT EXISTS idx_territory_battles_war ON territory_battles(war_id) WHERE war_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_territory_battles_deadline ON territory_battles(status,defense_deadline);

CREATE TABLE IF NOT EXISTS season_colony_stats (
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  points bigint NOT NULL DEFAULT 0,
  war_wins int NOT NULL DEFAULT 0 CHECK (war_wins >= 0),
  war_losses int NOT NULL DEFAULT 0 CHECK (war_losses >= 0),
  territories_captured int NOT NULL DEFAULT 0 CHECK (territories_captured >= 0),
  territories_defended int NOT NULL DEFAULT 0 CHECK (territories_defended >= 0),
  neutral_claims int NOT NULL DEFAULT 0 CHECK (neutral_claims >= 0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(season_id,colony_id)
);
CREATE INDEX IF NOT EXISTS idx_season_colony_stats_rank ON season_colony_stats(season_id,points DESC,territories_captured DESC,territories_defended DESC);

CREATE TABLE IF NOT EXISTS season_war_settlements (
  war_id uuid PRIMARY KEY REFERENCES colony_wars(id) ON DELETE CASCADE,
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  challenger_colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  defender_colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  winner_colony_id uuid REFERENCES colonies(id) ON DELETE SET NULL,
  settled_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS season_events (
  id bigserial PRIMARY KEY,
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  territory_id uuid REFERENCES territory_nodes(id) ON DELETE SET NULL,
  colony_id uuid REFERENCES colonies(id) ON DELETE SET NULL,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  event_type text NOT NULL CHECK (event_type IN ('season_started','neutral_claim','battle_declared','capture','defense','forfeit_capture','season_archived')),
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_season_events_recent ON season_events(season_id,created_at DESC);

CREATE TABLE IF NOT EXISTS season_rank_snapshots (
  season_id uuid NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  colony_id uuid NOT NULL REFERENCES colonies(id) ON DELETE CASCADE,
  rank int NOT NULL CHECK (rank > 0),
  points bigint NOT NULL DEFAULT 0,
  territories_held int NOT NULL DEFAULT 0,
  territories_captured int NOT NULL DEFAULT 0,
  territories_defended int NOT NULL DEFAULT 0,
  war_wins int NOT NULL DEFAULT 0,
  war_losses int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(season_id,colony_id),
  UNIQUE(season_id,rank)
);

ALTER TABLE colony_wars ADD COLUMN IF NOT EXISTS season_id uuid REFERENCES seasons(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_colony_wars_season ON colony_wars(season_id,status,created_at DESC);

INSERT INTO territory_nodes(code,name_i18n,tier,map_x,map_y,score_value) VALUES
('NORTH_GATE','{"en":"North Gate","fa":"دروازه شمالی","fr":"Porte du Nord","ar":"البوابة الشمالية","zh":"北门"}'::jsonb,1,18,12,100),
('CAMPUS','{"en":"Campus","fa":"پردیس","fr":"Campus","ar":"الحرم","zh":"校园"}'::jsonb,1,48,10,100),
('HARBOR','{"en":"Harbor","fa":"بندر","fr":"Port","ar":"الميناء","zh":"港口"}'::jsonb,1,82,18,100),
('BAZAAR','{"en":"Bazaar","fa":"بازار","fr":"Bazar","ar":"السوق","zh":"集市"}'::jsonb,1,15,42,110),
('GARDEN','{"en":"Garden","fa":"باغ","fr":"Jardin","ar":"الحديقة","zh":"花园"}'::jsonb,1,84,48,110),
('OASIS','{"en":"Oasis","fa":"واحه","fr":"Oasis","ar":"الواحة","zh":"绿洲"}'::jsonb,1,18,78,120),
('FORGE','{"en":"Forge","fa":"کوره","fr":"Forge","ar":"المسبك","zh":"锻造场"}'::jsonb,2,38,36,150),
('ARENA','{"en":"Arena","fa":"میدان","fr":"Arène","ar":"الساحة","zh":"竞技场"}'::jsonb,2,62,34,150),
('NEON','{"en":"Neon District","fa":"منطقه نئون","fr":"Quartier Néon","ar":"حي النيون","zh":"霓虹区"}'::jsonb,2,34,66,160),
('TOWER','{"en":"Sky Tower","fa":"برج آسمان","fr":"Tour du Ciel","ar":"برج السماء","zh":"天空塔"}'::jsonb,2,66,68,160),
('SUMMIT','{"en":"Summit","fa":"قله","fr":"Sommet","ar":"القمة","zh":"峰顶"}'::jsonb,3,50,88,220),
('CITADEL','{"en":"Citadel","fa":"دژ مرکزی","fr":"Citadelle","ar":"القلعة","zh":"中央堡垒"}'::jsonb,3,50,51,250)
ON CONFLICT(code) DO UPDATE SET name_i18n=EXCLUDED.name_i18n,tier=EXCLUDED.tier,map_x=EXCLUDED.map_x,map_y=EXCLUDED.map_y,score_value=EXCLUDED.score_value,enabled=true;

WITH e(a,b) AS (VALUES
('NORTH_GATE','CAMPUS'),('NORTH_GATE','FORGE'),('CAMPUS','FORGE'),('CAMPUS','ARENA'),('CAMPUS','HARBOR'),('HARBOR','ARENA'),('HARBOR','GARDEN'),
('BAZAAR','FORGE'),('BAZAAR','NEON'),('BAZAAR','OASIS'),('FORGE','ARENA'),('FORGE','CITADEL'),('FORGE','NEON'),('ARENA','CITADEL'),('ARENA','GARDEN'),('ARENA','TOWER'),
('GARDEN','TOWER'),('OASIS','NEON'),('NEON','CITADEL'),('NEON','SUMMIT'),('TOWER','CITADEL'),('TOWER','SUMMIT'),('CITADEL','SUMMIT')
), pairs AS (
  SELECT t1.id f,t2.id t FROM e JOIN territory_nodes t1 ON t1.code=e.a JOIN territory_nodes t2 ON t2.code=e.b
  UNION ALL
  SELECT t2.id f,t1.id t FROM e JOIN territory_nodes t1 ON t1.code=e.a JOIN territory_nodes t2 ON t2.code=e.b
)
INSERT INTO territory_edges(from_territory_id,to_territory_id)
SELECT f,t FROM pairs ON CONFLICT DO NOTHING;

DO $$
DECLARE s uuid;
BEGIN
  UPDATE seasons SET status='archived',archived_at=COALESCE(archived_at,now()) WHERE status='active' AND ends_at<=now();
  SELECT id INTO s FROM seasons WHERE status='active' AND starts_at<=now() AND ends_at>now() ORDER BY starts_at DESC LIMIT 1;
  IF s IS NULL THEN
    INSERT INTO seasons(code,title_i18n,status,starts_at,ends_at)
    VALUES('GENESIS_'||to_char(now(),'YYYYMMDDHH24MI'),
      '{"en":"Genesis Season","fa":"فصل آغاز","fr":"Saison Genèse","ar":"موسم البداية","zh":"创世纪赛季"}'::jsonb,
      'active',now(),now()+interval '28 days') RETURNING id INTO s;
    INSERT INTO season_events(season_id,event_type,payload) VALUES(s,'season_started','{"source":"migration"}'::jsonb);
  END IF;
  INSERT INTO season_territories(season_id,territory_id)
  SELECT s,id FROM territory_nodes WHERE enabled=true ON CONFLICT DO NOTHING;
END $$;
