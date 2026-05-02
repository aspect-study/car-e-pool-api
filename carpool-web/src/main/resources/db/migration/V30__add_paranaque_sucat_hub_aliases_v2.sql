-- ─────────────────────────────────────────────────────────────────────────────
-- V30: Additional aliases for V28 Parañaque Sucat Road Hubs
-- Covers common user typing patterns
-- ─────────────────────────────────────────────────────────────────────────────

INSERT IGNORE INTO hub_aliases (hub_id, alias)
SELECT h.id, v.alias
FROM hubs h
         INNER JOIN (
    SELECT 'PRQ_UPS5_MAIN_GATE'  AS hub_code, 'ups5'                     AS alias
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'upsv'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'ups5 upsv'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'sucat ups5'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'sucat upsv'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'ups5/upsv'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'valley 1'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'sucat valley 1'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'valley1'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'san antonio valley'
    UNION ALL SELECT 'PRQ_UNIHEALTH_SUCAT',  'unihealth'
    UNION ALL SELECT 'PRQ_UNIHEALTH_SUCAT',  'sucat unihealth'
    UNION ALL SELECT 'PRQ_UNIHEALTH_SUCAT',  'unihealth hospital'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'jaka plaza'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'jaka'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'sucat jaka plaza'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'sucat jaka'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'yp mall'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'yp'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'sucat yp mall'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'sucat yp'
    UNION ALL SELECT 'PRQ_VALLEY_2_GATE',    'valley 2'
    UNION ALL SELECT 'PRQ_VALLEY_2_GATE',    'valley2'
    UNION ALL SELECT 'PRQ_VALLEY_2_GATE',    'san antonio valley 2 gate'
) AS v ON h.code = v.hub_code
WHERE h.code IS NOT NULL;