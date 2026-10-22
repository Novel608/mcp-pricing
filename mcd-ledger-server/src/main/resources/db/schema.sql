-- 麦麦餐帐 — 事后对账主线 DDL（设计 §7 ER）
-- 金额一律 BIGINT（分），禁止 DECIMAL/FLOAT。
-- 每次启动重建，保证沙盒基准可复现（大赛演示：clone 即跑，fresh-per-boot）。

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS ledger_export_snapshot;
DROP TABLE IF EXISTS order_item_allocation;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS meal_ledger_order;
DROP TABLE IF EXISTS participant;
DROP TABLE IF EXISTS project_ref;
DROP TABLE IF EXISTS meal_ledger;
DROP TABLE IF EXISTS plan_recommendation;
DROP TABLE IF EXISTS plan_snapshot;
DROP TABLE IF EXISTS plan_item_selection;
DROP TABLE IF EXISTS plan_participant;
DROP TABLE IF EXISTS meal_plan;

CREATE TABLE meal_ledger (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  meal_time TIMESTAMP,
  operator_name VARCHAR(64),
  subsidy_limit_cents BIGINT,
  include_delivery_in_subsidy BOOLEAN DEFAULT FALSE,
  status VARCHAR(24),
  source_plan_id BIGINT NULL,
  expected_total_cents BIGINT NULL,
  created_at TIMESTAMP
);

CREATE TABLE meal_ledger_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_id BIGINT NOT NULL,
  mcp_order_id VARCHAR(64) NOT NULL,
  channel VARCHAR(32),
  order_status VARCHAR(24),
  data_confidence VARCHAR(32),
  paid_total_cents BIGINT,
  discount_cents BIGINT,
  delivery_fee_cents BIGINT,
  created_at TIMESTAMP,
  CONSTRAINT uk_ledger_mcp_order UNIQUE (ledger_id, mcp_order_id)
);
CREATE INDEX idx_order_ledger ON meal_ledger_order(ledger_id);

CREATE TABLE order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_order_id BIGINT NOT NULL,
  external_item_id VARCHAR(64),
  name VARCHAR(128),
  quantity INT,
  item_total_cents BIGINT,
  allocation_type VARCHAR(16),
  confidence VARCHAR(32)
);
CREATE INDEX idx_item_order ON order_item(ledger_order_id);

CREATE TABLE order_item_allocation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_item_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  weight INT,
  allocated_cents BIGINT
);
CREATE INDEX idx_alloc_item ON order_item_allocation(order_item_id);

CREATE TABLE participant (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_id BIGINT NOT NULL,
  display_name VARCHAR(64),
  person_ref VARCHAR(64),
  project_id BIGINT,
  sort_order INT
);
CREATE INDEX idx_participant_ledger ON participant(ledger_id);

CREATE TABLE project_ref (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_id BIGINT NOT NULL,
  name VARCHAR(128),
  code VARCHAR(64)
);
CREATE INDEX idx_project_ledger ON project_ref(ledger_id);

CREATE TABLE ledger_export_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_id BIGINT NOT NULL,
  version INT,
  payload_json CLOB,
  totals_json CLOB,
  created_at TIMESTAMP
);

CREATE TABLE audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_id BIGINT,
  action VARCHAR(48),
  target VARCHAR(128),
  before_json CLOB,
  after_json CLOB,
  operator VARCHAR(64),
  created_at TIMESTAMP
);
CREATE INDEX idx_meal_ledger_source_plan ON meal_ledger(source_plan_id);

-- ===== 选餐增量（plan 域，A1=L0 全量；设计 §3.5）=====
CREATE TABLE meal_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  store_id VARCHAR(64),
  store_name VARCHAR(128),
  channel VARCHAR(32),
  meal_time TIMESTAMP,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE plan_participant (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  person_ref VARCHAR(64),
  project_id BIGINT,
  sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_plan_participant_plan ON plan_participant(plan_id);

CREATE TABLE plan_item_selection (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  external_item_id VARCHAR(64) NOT NULL,
  name VARCHAR(128),
  quantity INT NOT NULL,
  unit_price_cents BIGINT NULL,
  spec_options_json CLOB
);
CREATE INDEX idx_plan_selection_plan ON plan_item_selection(plan_id);
CREATE INDEX idx_plan_selection_participant ON plan_item_selection(participant_id);

CREATE TABLE plan_recommendation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  type VARCHAR(24) NOT NULL,
  title VARCHAR(256) NOT NULL,
  reason_json CLOB NOT NULL,
  discount_cents BIGINT NOT NULL,
  added_cost_cents BIGINT NOT NULL,
  net_saving_cents BIGINT NULL,
  price_source VARCHAR(32),
  -- R6：netSavingCents ≤ 0 → 引擎自动置 TRUE，前端灰显不置顶（设计 T-3 反凑单诱导）
  not_worth_it BOOLEAN DEFAULT FALSE,
  accepted BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP
);
CREATE INDEX idx_plan_recommendation_plan ON plan_recommendation(plan_id);

CREATE TABLE plan_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  expected_subtotal_cents BIGINT NOT NULL,
  expected_discount_cents BIGINT NOT NULL,
  expected_delivery_fee_cents BIGINT NOT NULL,
  expected_total_cents BIGINT NOT NULL,
  per_person_expected_json CLOB NOT NULL,
  priced_at TIMESTAMP NOT NULL,
  menu_cache_version VARCHAR(64),
  items_hash VARCHAR(32),
  confidence VARCHAR(32),
  partial BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_plan_snapshot_plan ON plan_snapshot(plan_id);
