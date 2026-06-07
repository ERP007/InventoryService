-- SO(지점-본사 발주·입고) 흐름 가정 시드 데이터.
--
-- 시나리오:
--   * 본사(HQ-001)에 10개 SKU 초기 재고를 "이력 없이" 적재(current_stock은 출고 반영 후 최종 잔량).
--   * 지점 발주(SO)로 본사 재고가 OUTBOUND(감소), 지점 재고가 INBOUND(증가)되는 이력을 생성.
--   * 최종 stock: 본사 10건 + WH-SE-001(강남) 5건 + WH-SE-002(송파) 5건. 두 지점은 서로 다른 SKU.
-- 유지: warehouse, branch_location.   초기화: stock, stock_movement.
-- 실행: psql -f 이 파일 (ON_ERROR_STOP 권장). DB now ≈ 2026-06-07 기준으로 날짜를 최근 30일/7일에 걸치게 배치.

SET client_encoding = 'UTF8';

-- [스키마 보정] SO 한 라인은 본사 OUTBOUND + 지점 INBOUND 2행을 만들어 (source_ref, source_line_no)를 공유한다.
-- 멱등 제약에 warehouse_id를 더해 "라인×창고당 1행"으로 둔다(엔티티 StockMovementEntity도 동일하게 수정됨).
ALTER TABLE stock_movement DROP CONSTRAINT IF EXISTS uk_movement_source;
ALTER TABLE stock_movement ADD CONSTRAINT uk_movement_source UNIQUE (source_ref, source_line_no, warehouse_id);

BEGIN;

DELETE FROM stock_movement;
DELETE FROM stock;

-- 1) 본사(HQ-001) 초기 재고 — 이력 없음. current_stock = 출고 반영 후 최종 잔량.
INSERT INTO stock (sku, item_name, warehouse_id, current_stock, safety_stock, created_by, updated_by, created_at, updated_at, version) VALUES
 ('HMC-EN-00214','엔진오일 필터',  (SELECT id FROM warehouse WHERE code='HQ-001'), 430, 100, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-06-03T10:00:00+09',0),
 ('HMC-BR-00788','브레이크 패드',  (SELECT id FROM warehouse WHERE code='HQ-001'), 140,  80, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-15T10:00:00+09',0),
 ('HMC-SP-00125','점화플러그',     (SELECT id FROM warehouse WHERE code='HQ-001'), 275, 100, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-15T10:00:00+09',0),
 ('HMC-AF-00567','에어필터',       (SELECT id FROM warehouse WHERE code='HQ-001'), 120,  50, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-06-03T10:00:00+09',0),
 ('HMC-BT-00901','배터리',         (SELECT id FROM warehouse WHERE code='HQ-001'),  78,  30, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-15T10:00:00+09',0),
 ('HMC-OIL-5W30','엔진오일 5W30',  (SELECT id FROM warehouse WHERE code='HQ-001'), 300, 100, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-06-04T10:00:00+09',0),
 ('HMC-WP-00342','와이퍼 블레이드',(SELECT id FROM warehouse WHERE code='HQ-001'), 200,  80, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-16T10:00:00+09',0),
 ('HMC-CF-00432','캐빈필터',       (SELECT id FROM warehouse WHERE code='HQ-001'), 165,  60, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-16T10:00:00+09',0),
 ('HMC-TR-00111','변속기오일',     (SELECT id FROM warehouse WHERE code='HQ-001'),  60,  40, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-06-04T10:00:00+09',0),
 ('HMC-CL-00222','클러치 디스크',  (SELECT id FROM warehouse WHERE code='HQ-001'),   0,  30, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-16T10:00:00+09',0);

-- 2) 지점 재고 (WH-SE-001 강남 / WH-SE-002 송파) — 입고 누적 후 최종 잔량.
INSERT INTO stock (sku, item_name, warehouse_id, current_stock, safety_stock, created_by, updated_by, created_at, updated_at, version) VALUES
 -- WH-SE-001 (강남) : EN, BR, SP, AF, BT
 ('HMC-EN-00214','엔진오일 필터',  (SELECT id FROM warehouse WHERE code='WH-SE-001'), 70, 50, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-06-03T11:00:00+09',0),
 ('HMC-BR-00788','브레이크 패드',  (SELECT id FROM warehouse WHERE code='WH-SE-001'), 60, 40, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-05-15T11:00:00+09',0),
 ('HMC-SP-00125','점화플러그',     (SELECT id FROM warehouse WHERE code='WH-SE-001'), 25, 60, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-05-15T11:00:00+09',0),
 ('HMC-AF-00567','에어필터',       (SELECT id FROM warehouse WHERE code='WH-SE-001'), 30, 30, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-06-03T11:00:00+09',0),
 ('HMC-BT-00901','배터리',         (SELECT id FROM warehouse WHERE code='WH-SE-001'), 12, 20, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-05-15T11:00:00+09',0),
 -- WH-SE-002 (송파) : OIL, WP, CF, TR, CL
 ('HMC-OIL-5W30','엔진오일 5W30',  (SELECT id FROM warehouse WHERE code='WH-SE-002'),100, 60, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-06-04T11:00:00+09',0),
 ('HMC-WP-00342','와이퍼 블레이드',(SELECT id FROM warehouse WHERE code='WH-SE-002'), 50, 50, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-05-16T11:00:00+09',0),
 ('HMC-CF-00432','캐빈필터',       (SELECT id FROM warehouse WHERE code='WH-SE-002'), 15, 40, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-05-16T11:00:00+09',0),
 ('HMC-TR-00111','변속기오일',     (SELECT id FROM warehouse WHERE code='WH-SE-002'), 60, 45, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-06-04T11:00:00+09',0),
 ('HMC-CL-00222','클러치 디스크',  (SELECT id FROM warehouse WHERE code='WH-SE-002'), 80, 25, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-05-16T11:00:00+09',0);

-- 3) SO 이동 이력: 각 라인 = 본사 OUTBOUND(-) + 지점 INBOUND(+). reason은 SO 흐름이라 NULL.
INSERT INTO stock_movement (sku, warehouse_id, delta, type, reason, source_ref, source_line_no, stock_after, memo, executor_emp_no, performed_at) VALUES
 -- SO-202605-00001 : 강남(WH-SE-001) 발주, 2026-05-15
 ('HMC-EN-00214',(SELECT id FROM warehouse WHERE code='HQ-001'),    -40,'OUTBOUND',NULL,'SO-202605-00001',1,460,NULL,'HMC1001','2026-05-15T10:00:00+09'),
 ('HMC-EN-00214',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  40,'INBOUND', NULL,'SO-202605-00001',1, 40,NULL,'HMC2001','2026-05-15T11:00:00+09'),
 ('HMC-BR-00788',(SELECT id FROM warehouse WHERE code='HQ-001'),    -60,'OUTBOUND',NULL,'SO-202605-00001',2,140,NULL,'HMC1001','2026-05-15T10:00:00+09'),
 ('HMC-BR-00788',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  60,'INBOUND', NULL,'SO-202605-00001',2, 60,NULL,'HMC2001','2026-05-15T11:00:00+09'),
 ('HMC-SP-00125',(SELECT id FROM warehouse WHERE code='HQ-001'),    -25,'OUTBOUND',NULL,'SO-202605-00001',3,275,NULL,'HMC1001','2026-05-15T10:00:00+09'),
 ('HMC-SP-00125',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  25,'INBOUND', NULL,'SO-202605-00001',3, 25,NULL,'HMC2001','2026-05-15T11:00:00+09'),
 ('HMC-AF-00567',(SELECT id FROM warehouse WHERE code='HQ-001'),    -20,'OUTBOUND',NULL,'SO-202605-00001',4,130,NULL,'HMC1001','2026-05-15T10:00:00+09'),
 ('HMC-AF-00567',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  20,'INBOUND', NULL,'SO-202605-00001',4, 20,NULL,'HMC2001','2026-05-15T11:00:00+09'),
 ('HMC-BT-00901',(SELECT id FROM warehouse WHERE code='HQ-001'),    -12,'OUTBOUND',NULL,'SO-202605-00001',5, 78,NULL,'HMC1001','2026-05-15T10:00:00+09'),
 ('HMC-BT-00901',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  12,'INBOUND', NULL,'SO-202605-00001',5, 12,NULL,'HMC2001','2026-05-15T11:00:00+09'),
 -- SO-202605-00002 : 송파(WH-SE-002) 발주, 2026-05-16
 ('HMC-OIL-5W30',(SELECT id FROM warehouse WHERE code='HQ-001'),    -80,'OUTBOUND',NULL,'SO-202605-00002',1,320,NULL,'HMC1001','2026-05-16T10:00:00+09'),
 ('HMC-OIL-5W30',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  80,'INBOUND', NULL,'SO-202605-00002',1, 80,NULL,'HMC2002','2026-05-16T11:00:00+09'),
 ('HMC-WP-00342',(SELECT id FROM warehouse WHERE code='HQ-001'),    -50,'OUTBOUND',NULL,'SO-202605-00002',2,200,NULL,'HMC1001','2026-05-16T10:00:00+09'),
 ('HMC-WP-00342',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  50,'INBOUND', NULL,'SO-202605-00002',2, 50,NULL,'HMC2002','2026-05-16T11:00:00+09'),
 ('HMC-CF-00432',(SELECT id FROM warehouse WHERE code='HQ-001'),    -15,'OUTBOUND',NULL,'SO-202605-00002',3,165,NULL,'HMC1001','2026-05-16T10:00:00+09'),
 ('HMC-CF-00432',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  15,'INBOUND', NULL,'SO-202605-00002',3, 15,NULL,'HMC2002','2026-05-16T11:00:00+09'),
 ('HMC-TR-00111',(SELECT id FROM warehouse WHERE code='HQ-001'),    -35,'OUTBOUND',NULL,'SO-202605-00002',4, 85,NULL,'HMC1001','2026-05-16T10:00:00+09'),
 ('HMC-TR-00111',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  35,'INBOUND', NULL,'SO-202605-00002',4, 35,NULL,'HMC2002','2026-05-16T11:00:00+09'),
 ('HMC-CL-00222',(SELECT id FROM warehouse WHERE code='HQ-001'),    -80,'OUTBOUND',NULL,'SO-202605-00002',5,  0,NULL,'HMC1001','2026-05-16T10:00:00+09'),
 ('HMC-CL-00222',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  80,'INBOUND', NULL,'SO-202605-00002',5, 80,NULL,'HMC2002','2026-05-16T11:00:00+09'),
 -- SO-202606-00010 : 강남(WH-SE-001) 재발주, 2026-06-03 (최근 7일 이내)
 ('HMC-EN-00214',(SELECT id FROM warehouse WHERE code='HQ-001'),    -30,'OUTBOUND',NULL,'SO-202606-00010',1,430,NULL,'HMC1001','2026-06-03T10:00:00+09'),
 ('HMC-EN-00214',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  30,'INBOUND', NULL,'SO-202606-00010',1, 70,NULL,'HMC2001','2026-06-03T11:00:00+09'),
 ('HMC-AF-00567',(SELECT id FROM warehouse WHERE code='HQ-001'),    -10,'OUTBOUND',NULL,'SO-202606-00010',2,120,NULL,'HMC1001','2026-06-03T10:00:00+09'),
 ('HMC-AF-00567',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  10,'INBOUND', NULL,'SO-202606-00010',2, 30,NULL,'HMC2001','2026-06-03T11:00:00+09'),
 -- SO-202606-00011 : 송파(WH-SE-002) 재발주, 2026-06-04 (최근 7일 이내)
 ('HMC-OIL-5W30',(SELECT id FROM warehouse WHERE code='HQ-001'),    -20,'OUTBOUND',NULL,'SO-202606-00011',1,300,NULL,'HMC1001','2026-06-04T10:00:00+09'),
 ('HMC-OIL-5W30',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  20,'INBOUND', NULL,'SO-202606-00011',1,100,NULL,'HMC2002','2026-06-04T11:00:00+09'),
 ('HMC-TR-00111',(SELECT id FROM warehouse WHERE code='HQ-001'),    -25,'OUTBOUND',NULL,'SO-202606-00011',2, 60,NULL,'HMC1001','2026-06-04T10:00:00+09'),
 ('HMC-TR-00111',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  25,'INBOUND', NULL,'SO-202606-00011',2, 60,NULL,'HMC2002','2026-06-04T11:00:00+09');

COMMIT;
