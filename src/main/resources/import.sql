-- 로컬 시드 데이터. Hibernate가 ddl-auto=create/create-drop로 스키마를 새로 만든 직후 1회 자동 실행한다.
-- (update/validate에선 실행되지 않음 → 평상시 데이터를 건드리지 않는다.)
-- 스키마는 엔티티가 정의하므로 여기엔 DDL 없이 INSERT만 둔다. 삽입 순서 = 의존 순서(지점→창고→재고→이력).
-- 멀티라인/UTF-8 파싱은 application.yaml의 hbm2ddl.import_files_sql_extractor·charset_name 설정에 의존한다.

-- 1) 지점(branch_location)
INSERT INTO branch_location (name) VALUES
 ('서울 강남지점'),
 ('서울 송파지점'),
 ('부산 해운대지점'),
 ('대전 둔산지점'),
 ('광주 첨단지점'),
 ('서울 금천지점1'),
 ('서울 강남지점2');

-- 2) 창고(warehouse). 본사(HQ)는 branch 없음, 대리점(DEALER)은 지점에 매핑. version은 낙관락 초기값 0.
INSERT INTO warehouse (code, name, type, branch_id, active, version) VALUES
 ('HQ-001',    '본사',           'HQ',     NULL, true,  0),
 ('WH-SE-001', '서울 강남창고',   'DEALER', (SELECT id FROM branch_location WHERE name = '서울 강남지점'),   true,  0),
 ('WH-SE-002', '서울 송파창고',   'DEALER', (SELECT id FROM branch_location WHERE name = '서울 송파지점'),   true,  0),
 ('WH-BS-001', '부산 해운대창고', 'DEALER', (SELECT id FROM branch_location WHERE name = '부산 해운대지점'), false, 0),
 ('WH-DJ-001', '대전 둔산창고',   'DEALER', (SELECT id FROM branch_location WHERE name = '대전 둔산지점'),   true,  0),
 ('WH-GJ-001', '광주 첨단창고',   'DEALER', (SELECT id FROM branch_location WHERE name = '광주 첨단지점'),   true,  0),
 ('WH-SE-003', '서울 강남창고2',  'DEALER', (SELECT id FROM branch_location WHERE name = '서울 강남지점'),   true,  0);

-- 3) 재고(stock). current_stock은 출고 반영 후 최종 잔량(이력의 stock_after와 일치).
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
 ('HMC-CL-00222','클러치 디스크',  (SELECT id FROM warehouse WHERE code='HQ-001'),   0,  30, 'admin001','HMC1001','2026-05-10T09:00:00+09','2026-05-16T10:00:00+09',0),
 ('HMC-EN-00214','엔진오일 필터',  (SELECT id FROM warehouse WHERE code='WH-SE-001'), 70, 50, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-06-03T11:00:00+09',0),
 ('HMC-BR-00788','브레이크 패드',  (SELECT id FROM warehouse WHERE code='WH-SE-001'), 60, 40, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-05-15T11:00:00+09',0),
 ('HMC-SP-00125','점화플러그',     (SELECT id FROM warehouse WHERE code='WH-SE-001'), 25, 60, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-05-15T11:00:00+09',0),
 ('HMC-AF-00567','에어필터',       (SELECT id FROM warehouse WHERE code='WH-SE-001'), 30, 30, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-06-03T11:00:00+09',0),
 ('HMC-BT-00901','배터리',         (SELECT id FROM warehouse WHERE code='WH-SE-001'), 12, 20, 'HMC2001','HMC2001','2026-05-15T11:00:00+09','2026-05-15T11:00:00+09',0),
 ('HMC-OIL-5W30','엔진오일 5W30',  (SELECT id FROM warehouse WHERE code='WH-SE-002'),100, 60, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-06-04T11:00:00+09',0),
 ('HMC-WP-00342','와이퍼 블레이드',(SELECT id FROM warehouse WHERE code='WH-SE-002'), 50, 50, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-05-16T11:00:00+09',0),
 ('HMC-CF-00432','캐빈필터',       (SELECT id FROM warehouse WHERE code='WH-SE-002'), 15, 40, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-05-16T11:00:00+09',0),
 ('HMC-TR-00111','변속기오일',     (SELECT id FROM warehouse WHERE code='WH-SE-002'), 60, 45, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-06-04T11:00:00+09',0),
 ('HMC-CL-00222','클러치 디스크',  (SELECT id FROM warehouse WHERE code='WH-SE-002'), 80, 25, 'HMC2002','HMC2002','2026-05-16T11:00:00+09','2026-05-16T11:00:00+09',0);

-- 4) 이동 이력(stock_movement). 각 SO 라인 = 본사 OUTBOUND(-) + 지점 INBOUND(+). reason은 SO 흐름이라 NULL.
INSERT INTO stock_movement (sku, warehouse_id, delta, type, reason, source_ref, source_line_no, stock_after, memo, executor_emp_no, performed_at) VALUES
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
 ('HMC-EN-00214',(SELECT id FROM warehouse WHERE code='HQ-001'),    -30,'OUTBOUND',NULL,'SO-202606-00010',1,430,NULL,'HMC1001','2026-06-03T10:00:00+09'),
 ('HMC-EN-00214',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  30,'INBOUND', NULL,'SO-202606-00010',1, 70,NULL,'HMC2001','2026-06-03T11:00:00+09'),
 ('HMC-AF-00567',(SELECT id FROM warehouse WHERE code='HQ-001'),    -10,'OUTBOUND',NULL,'SO-202606-00010',2,120,NULL,'HMC1001','2026-06-03T10:00:00+09'),
 ('HMC-AF-00567',(SELECT id FROM warehouse WHERE code='WH-SE-001'),  10,'INBOUND', NULL,'SO-202606-00010',2, 30,NULL,'HMC2001','2026-06-03T11:00:00+09'),
 ('HMC-OIL-5W30',(SELECT id FROM warehouse WHERE code='HQ-001'),    -20,'OUTBOUND',NULL,'SO-202606-00011',1,300,NULL,'HMC1001','2026-06-04T10:00:00+09'),
 ('HMC-OIL-5W30',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  20,'INBOUND', NULL,'SO-202606-00011',1,100,NULL,'HMC2002','2026-06-04T11:00:00+09'),
 ('HMC-TR-00111',(SELECT id FROM warehouse WHERE code='HQ-001'),    -25,'OUTBOUND',NULL,'SO-202606-00011',2, 60,NULL,'HMC1001','2026-06-04T10:00:00+09'),
 ('HMC-TR-00111',(SELECT id FROM warehouse WHERE code='WH-SE-002'),  25,'INBOUND', NULL,'SO-202606-00011',2, 60,NULL,'HMC2002','2026-06-04T11:00:00+09');
