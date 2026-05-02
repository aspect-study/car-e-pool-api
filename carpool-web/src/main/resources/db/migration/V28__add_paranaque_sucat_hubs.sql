-- ─────────────────────────────────────────────────────────────────────────────
-- V28: Add Parañaque Sucat Road Hubs
-- PRQ_SUCAT_EVANGELISTA excluded — already exists in V20
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO hubs (code, name, area, status, created_at, updated_at) VALUES

-- SUCAT ROAD CENTRAL
('PRQ_UPS5_MAIN_GATE',    'UPS5 / UPSV (Main Gate)',             'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_VALLEY_1_HALL',     'San Antonio Valley 1 (Brgy Hall)',    'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_UNIHEALTH_SUCAT',   'Unihealth-Parañaque Hospital',        'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_JAKA_PLAZA',        'Jaka Plaza (Sucat Road)',             'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_YP_MALL_SUCAT',     'YP Mall (Sucat Road)',                'Paranaque', 'ACTIVE', NOW(6), NOW(6)),

-- SUCAT ROAD PRECISION
('PRQ_SUCAT_SAMPAGUITA',  'Sucat Rd / Sampaguita (UPS2 Area)',   'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_VALLEY_2_GATE',     'San Antonio Valley 2 (Gate)',         'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_ELORDE_SUCAT',      'Elorde Sports Center (Sucat)',        'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_LP_HYPERMARKET',    'Lopez / SM Hypermarket (Sucat Rd)',   'Paranaque', 'ACTIVE', NOW(6), NOW(6)),

-- WEST SERVICE ROAD / INTERCHANGE ACCESS
('PRQ_WSR_KNOTS_AREA',    '40 Knots (West Service Rd)',          'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_SUCAT_INTER_WSR',   'Sucat Interchange (WSR Side)',        'Paranaque', 'ACTIVE', NOW(6), NOW(6)),
('PRQ_BLOOMFIELDS_GATE',  'Bloomfields Subdivision (Main Gate)', 'Paranaque', 'ACTIVE', NOW(6), NOW(6));