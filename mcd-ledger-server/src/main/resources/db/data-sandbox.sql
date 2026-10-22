-- 麦麦餐帐 — 沙盒基准数据集（设计 §9，两单·六人·两项目，单位：分）
-- 作为真实持久化的 ledger（id=1），由 schema 初始化加载；无需 Token。
--
-- 项目归属按设计 §9.3/§9.4 数值口径：项目A（核心账务重构）= 林/陈/王/孙；项目B（AI助手接入）= 赵/周。
-- （任务书散文「赵→A、孙→B」与目标数字 15716/7084 矛盾，按数值口径落库。）
--
-- 插入顺序即引擎的「首次出现顺序」与 PennyAllocator 平局派发下标，务必保持：
--   订单1 物品序：巨无霸(林)/巨无霸(陈)/吉士(王)/吉士(赵)/麦乐鸡(林陈王赵)
--   订单2 物品序：板烧(孙)/板烧(周)/薯条(孙周陈林王赵)  ← 薯条权重序固定，尾差 467×4 + 466×2

-- T05 演示闭环：同一餐次、同一批人，本账演示为「由选餐方案 id=1 转出」，
-- 故带 source_plan_id=1 与 expected_total_cents=19650（官方核价基线）。
-- 预期 19650（点餐前选餐）vs 实际 22800（真实订单）→ 超支 3150，即「点餐时改主意」的真实场景。
INSERT INTO meal_ledger (id, title, meal_time, operator_name, subsidy_limit_cents, include_delivery_in_subsidy, status, source_plan_id, expected_total_cents, created_at)
VALUES (1, '周六加班餐 · 两单六人（含选餐预算基线）', TIMESTAMP '2026-10-22 20:00:00', '寇豆码', 3500, FALSE, 'CONFIRMED', 1, 19650, TIMESTAMP '2026-10-22 20:05:00');

INSERT INTO project_ref (id, ledger_id, name, code) VALUES
  (1, 1, '核心账务重构', 'PROJ-CORE'),
  (2, 1, 'AI助手接入', 'PROJ-AI');

INSERT INTO participant (id, ledger_id, display_name, person_ref, project_id, sort_order) VALUES
  (1, 1, '林工', 'lin',  1, 0),
  (2, 1, '陈工', 'chen', 1, 1),
  (3, 1, '王工', 'wang', 1, 2),
  (4, 1, '赵工', 'zhao', 2, 3),
  (5, 1, '孙工', 'sun',  1, 4),
  (6, 1, '周工', 'zhou', 2, 5);

INSERT INTO meal_ledger_order (id, ledger_id, mcp_order_id, channel, order_status, data_confidence, paid_total_cents, discount_cents, delivery_fee_cents, created_at) VALUES
  (1, 1, 'MCD-20261022-01', '麦乐送',   'COMPLETED', 'VERIFIED_HISTORICAL', 14800, -3000, 900, TIMESTAMP '2026-10-22 20:00:00'),
  (2, 1, 'MCD-20261022-02', '到店自提', 'COMPLETED', 'VERIFIED_HISTORICAL',  8000, -1400,   0, TIMESTAMP '2026-10-22 20:25:00');

-- 订单1 餐品
INSERT INTO order_item (id, ledger_order_id, external_item_id, name, quantity, item_total_cents, allocation_type, confidence) VALUES
  (101, 1, 'BIGMAC_COMBO',    '巨无霸套餐',     1, 3500, 'PERSONAL', 'VERIFIED_HISTORICAL'),
  (102, 1, 'BIGMAC_COMBO',    '巨无霸套餐',     1, 3500, 'PERSONAL', 'VERIFIED_HISTORICAL'),
  (103, 1, 'DBL_CHEESE_COMBO','双层吉士堡套餐', 1, 3000, 'PERSONAL', 'VERIFIED_HISTORICAL'),
  (104, 1, 'DBL_CHEESE_COMBO','双层吉士堡套餐', 1, 3000, 'PERSONAL', 'VERIFIED_HISTORICAL'),
  (105, 1, 'MCNAUGGETS_20PC', '麦乐鸡20块',     1, 3900, 'SHARED',   'VERIFIED_HISTORICAL');

-- 订单2 餐品
INSERT INTO order_item (id, ledger_order_id, external_item_id, name, quantity, item_total_cents, allocation_type, confidence) VALUES
  (201, 2, 'GRILLED_CHICKEN_COMBO', '板烧鸡腿堡套餐', 1, 3300, 'PERSONAL', 'VERIFIED_HISTORICAL'),
  (202, 2, 'GRILLED_CHICKEN_COMBO', '板烧鸡腿堡套餐', 1, 3300, 'PERSONAL', 'VERIFIED_HISTORICAL'),
  (203, 2, 'LARGE_FRIES',           '霸王薯条大份',   1, 2800, 'SHARED',   'VERIFIED_HISTORICAL');

-- 订单1 分摊（PERSONAL 整份归人；麦乐鸡 4 人均分各 975）
INSERT INTO order_item_allocation (id, order_item_id, participant_id, weight, allocated_cents) VALUES
  (1001, 101, 1, 1, 3500),
  (1002, 102, 2, 1, 3500),
  (1003, 103, 3, 1, 3000),
  (1004, 104, 4, 1, 3000),
  (1005, 105, 1, 1,  975),
  (1006, 105, 2, 1,  975),
  (1007, 105, 3, 1,  975),
  (1008, 105, 4, 1,  975);

-- 订单2 分摊（板烧整份归人；薯条权重序 孙/周/陈/林/王/赵 → 467×4 + 466×2 = 2800）
INSERT INTO order_item_allocation (id, order_item_id, participant_id, weight, allocated_cents) VALUES
  (2001, 201, 5, 1, 3300),
  (2002, 202, 6, 1, 3300),
  (2003, 203, 5, 1,  467),
  (2004, 203, 6, 1,  467),
  (2005, 203, 2, 1,  467),
  (2006, 203, 1, 1,  467),
  (2007, 203, 3, 1,  466),
  (2008, 203, 4, 1,  466);

-- ===== 沙盒选餐方案（plan id=1，与对账 demo 同源六人两项目；无需 Token）=====
INSERT INTO meal_plan (id, title, store_id, store_name, channel, meal_time, status, created_at)
VALUES (1, '周六加班餐 · 组队选餐方案', '190001', '麦当劳(科技园店)', '麦乐送', TIMESTAMP '2026-10-22 20:00:00', 'DRAFT', TIMESTAMP '2026-10-22 19:50:00');

INSERT INTO plan_participant (id, plan_id, display_name, person_ref, project_id, sort_order) VALUES
  (1, 1, '林工', 'lin',  1, 0),
  (2, 1, '陈工', 'chen', 1, 1),
  (3, 1, '王工', 'wang', 1, 2),
  (4, 1, '赵工', 'zhao', 2, 3),
  (5, 1, '孙工', 'sun',  1, 4),
  (6, 1, '周工', 'zhou', 2, 5);

-- 选餐项（unit_price_cents 为分；code 与单价**全部取自官方 query-meals 真实抓取值**，
-- 保证「菜单 → 选餐 → 推荐 → 官方核价」四者口径同一，demo 数字自洽）
INSERT INTO plan_item_selection (id, plan_id, participant_id, external_item_id, name, quantity, unit_price_cents, spec_options_json) VALUES
  (1, 1, 1, '1440',      '麦辣鸡腿汉堡', 1, 2250, NULL),
  (2, 1, 1, '4810',      '薯条',         1, 1350, NULL),
  (3, 1, 2, '1440',      '麦辣鸡腿汉堡', 1, 2250, NULL),
  (4, 1, 2, '4810',      '薯条',         1, 1350, NULL),
  (5, 1, 3, '1406',      '板烧鸡腿堡',   1, 2350, NULL),
  (6, 1, 3, '9900008751','可乐',         1,  950, NULL),
  (7, 1, 4, '1100',      '巨无霸',       1, 2550, NULL),
  (8, 1, 4, '9900008751','可乐',         1,  950, NULL),
  (9, 1, 5, '1401',      '麦乐鸡',       1, 1400, NULL),
  (10,1, 5, '1600',      '麦香鱼',       1, 2250, NULL),
  (11,1, 6, '1700',      '麦辣鸡翅',     1, 1500, NULL),
  (12,1, 6, '515837',    '圆筒冰淇淋',   1,  500, NULL);
