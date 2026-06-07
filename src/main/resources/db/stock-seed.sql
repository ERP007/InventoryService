-- 재고(stock) 초기 테스트 데이터: WH-SE-002 8건 + HQ-001 8건 (총 16건)
--
-- 실행 전제:
--   1) 앱을 local/운영 프로파일로 한 번 기동해 stock 테이블이 생성돼 있어야 한다(ddl-auto: update).
--   2) warehouse 테이블에 code = 'WH-SE-002', 'HQ-001' 행이 존재해야 한다(warehouse_id 연결용).
-- 실행: psql / DB 클라이언트에서 이 파일을 직접 실행. ON CONFLICT로 재실행 안전.
-- 상태 분포(검증용): NORMAL(수량≥안전), LOW(0<수량<안전), OUT(수량=0)이 골고루 섞여 있다.

INSERT INTO stock
    (sku, item_name, warehouse_id, current_stock, safety_stock,
     created_by, updated_by, created_at, updated_at, version)
VALUES
    -- WH-SE-002 (8건)
    ('HMC-EN-00214', '엔진오일 필터',   (SELECT id FROM warehouse WHERE code = 'WH-SE-002'), 120,  50, 'admin002', 'admin002', '2026-05-20T09:00:00Z', '2026-05-20T09:00:00Z', 0),
    ('HMC-BR-00788', '브레이크 패드',   (SELECT id FROM warehouse WHERE code = 'WH-SE-002'),  30,  40, 'admin002', 'admin002', '2026-05-20T10:00:00Z', '2026-05-21T10:00:00Z', 0),
    ('HMC-OIL-5W30', '엔진오일 5W30',   (SELECT id FROM warehouse WHERE code = 'WH-SE-002'),   0,  60, 'admin002', 'admin002', '2026-05-20T11:00:00Z', '2026-05-22T11:00:00Z', 0),
    ('HMC-WP-00342', '와이퍼 블레이드', (SELECT id FROM warehouse WHERE code = 'WH-SE-002'),  50,  50, 'admin002', 'admin002', '2026-05-20T12:00:00Z', '2026-05-20T12:00:00Z', 0),
    ('HMC-SP-00125', '점화플러그',      (SELECT id FROM warehouse WHERE code = 'WH-SE-002'), 200,  80, 'admin002', 'admin002', '2026-05-20T13:00:00Z', '2026-05-20T13:00:00Z', 0),
    ('HMC-AF-00567', '에어필터',        (SELECT id FROM warehouse WHERE code = 'WH-SE-002'),  15,  30, 'admin002', 'admin002', '2026-05-20T14:00:00Z', '2026-05-23T14:00:00Z', 0),
    ('HMC-BT-00901', '배터리',          (SELECT id FROM warehouse WHERE code = 'WH-SE-002'),   8,  10, 'admin002', 'admin002', '2026-05-20T15:00:00Z', '2026-05-24T15:00:00Z', 0),
    ('HMC-CF-00432', '캐빈필터',        (SELECT id FROM warehouse WHERE code = 'WH-SE-002'),  75,  25, 'admin002', 'admin002', '2026-05-20T16:00:00Z', '2026-05-20T16:00:00Z', 0),

    -- HQ-001 (8건)
    ('HMC-EN-00214', '엔진오일 필터',   (SELECT id FROM warehouse WHERE code = 'HQ-001'), 500, 100, 'admin002', 'admin002', '2026-05-19T09:00:00Z', '2026-05-19T09:00:00Z', 0),
    ('HMC-TR-00111', '변속기오일',      (SELECT id FROM warehouse WHERE code = 'HQ-001'),   0,  40, 'admin002', 'admin002', '2026-05-19T10:00:00Z', '2026-05-25T10:00:00Z', 0),
    ('HMC-CL-00222', '클러치 디스크',   (SELECT id FROM warehouse WHERE code = 'HQ-001'),  45,  45, 'admin002', 'admin002', '2026-05-19T11:00:00Z', '2026-05-19T11:00:00Z', 0),
    ('HMC-RD-00333', '라디에이터',      (SELECT id FROM warehouse WHERE code = 'HQ-001'),  12,  20, 'admin002', 'admin002', '2026-05-19T12:00:00Z', '2026-05-26T12:00:00Z', 0),
    ('HMC-AL-00444', '얼터네이터',      (SELECT id FROM warehouse WHERE code = 'HQ-001'),  60,  30, 'admin002', 'admin002', '2026-05-19T13:00:00Z', '2026-05-19T13:00:00Z', 0),
    ('HMC-ST-00555', '스타터모터',      (SELECT id FROM warehouse WHERE code = 'HQ-001'),   5,  15, 'admin002', 'admin002', '2026-05-19T14:00:00Z', '2026-05-27T14:00:00Z', 0),
    ('HMC-TM-00666', '타이밍벨트',      (SELECT id FROM warehouse WHERE code = 'HQ-001'),  90,  50, 'admin002', 'admin002', '2026-05-19T15:00:00Z', '2026-05-19T15:00:00Z', 0),
    ('HMC-SH-00777', '쇼크업소버',      (SELECT id FROM warehouse WHERE code = 'HQ-001'),   0,  25, 'admin002', 'admin002', '2026-05-19T16:00:00Z', '2026-05-28T16:00:00Z', 0)
ON CONFLICT (sku, warehouse_id) DO NOTHING;
