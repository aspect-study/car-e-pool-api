-- ─────────────────────────────────────────────────────────────────────────────
-- V29: Aliases for V28 Parañaque Sucat Road Hubs
-- Aliases lowercase to match HubMatcher search behavior.
-- Uses INSERT IGNORE to skip duplicates safely.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT IGNORE INTO hub_aliases (hub_id, alias)
SELECT h.id, v.alias
FROM hubs h
         INNER JOIN (
    SELECT 'PRQ_UPS5_MAIN_GATE'   AS hub_code, 'ups5 paranaque'            AS alias
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'upsv paranaque'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'up south 5 paranaque'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'ups 5 sucat'
    UNION ALL SELECT 'PRQ_UPS5_MAIN_GATE',   'up south village 5'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'san antonio valley 1'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'sav 1 brgy hall'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'valley 1 paranaque'
    UNION ALL SELECT 'PRQ_VALLEY_1_HALL',    'brgy hall valley 1'
    UNION ALL SELECT 'PRQ_UNIHEALTH_SUCAT',  'unihealth paranaque'
    UNION ALL SELECT 'PRQ_UNIHEALTH_SUCAT',  'unihealth hospital sucat'
    UNION ALL SELECT 'PRQ_UNIHEALTH_SUCAT',  'unihealth sucat road'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'jaka plaza paranaque'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'jaka sucat road'
    UNION ALL SELECT 'PRQ_JAKA_PLAZA',       'jaka plaza sucat'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'yp mall paranaque'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'yp mall sucat road'
    UNION ALL SELECT 'PRQ_YP_MALL_SUCAT',    'yellow pages mall sucat'
    UNION ALL SELECT 'PRQ_SUCAT_SAMPAGUITA', 'sampaguita sucat paranaque'
    UNION ALL SELECT 'PRQ_SUCAT_SAMPAGUITA', 'ups2 paranaque'
    UNION ALL SELECT 'PRQ_SUCAT_SAMPAGUITA', 'up south 2 sucat'
    UNION ALL SELECT 'PRQ_SUCAT_SAMPAGUITA', 'sucat sampaguita road'
    UNION ALL SELECT 'PRQ_VALLEY_2_GATE',    'san antonio valley 2'
    UNION ALL SELECT 'PRQ_VALLEY_2_GATE',    'sav 2 gate paranaque'
    UNION ALL SELECT 'PRQ_VALLEY_2_GATE',    'valley 2 paranaque gate'
    UNION ALL SELECT 'PRQ_ELORDE_SUCAT',     'elorde sucat paranaque'
    UNION ALL SELECT 'PRQ_ELORDE_SUCAT',     'elorde sports sucat'
    UNION ALL SELECT 'PRQ_ELORDE_SUCAT',     'elorde gym sucat'
    UNION ALL SELECT 'PRQ_LP_HYPERMARKET',   'sm hypermarket sucat'
    UNION ALL SELECT 'PRQ_LP_HYPERMARKET',   'lopez sucat paranaque'
    UNION ALL SELECT 'PRQ_LP_HYPERMARKET',   'hypermarket sucat road'
    UNION ALL SELECT 'PRQ_LP_HYPERMARKET',   'sm sucat hypermarket'
    UNION ALL SELECT 'PRQ_WSR_KNOTS_AREA',   '40 knots wsr paranaque'
    UNION ALL SELECT 'PRQ_WSR_KNOTS_AREA',   'wsr 40 knots area'
    UNION ALL SELECT 'PRQ_WSR_KNOTS_AREA',   'forty knots west service'
    UNION ALL SELECT 'PRQ_SUCAT_INTER_WSR',  'sucat interchange wsr paranaque'
    UNION ALL SELECT 'PRQ_SUCAT_INTER_WSR',  'sucat wsr interchange'
    UNION ALL SELECT 'PRQ_SUCAT_INTER_WSR',  'wsr sucat interchange'
    UNION ALL SELECT 'PRQ_BLOOMFIELDS_GATE', 'bloomfields paranaque'
    UNION ALL SELECT 'PRQ_BLOOMFIELDS_GATE', 'bloomfields subdivision sucat'
    UNION ALL SELECT 'PRQ_BLOOMFIELDS_GATE', 'bloomfields main gate sucat'
) AS v ON h.code = v.hub_code
WHERE h.code IS NOT NULL;