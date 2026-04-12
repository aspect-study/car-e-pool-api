-- ============================================================
-- V2__seed_hubs.sql
-- Initial hub seed for South Metro Manila → Taguig / Makati corridor
-- Add more hubs via the admin API as the community grows
-- ============================================================

INSERT INTO hubs (code, name, area, status, created_at, updated_at) VALUES

-- Muntinlupa
('ALABANG_TOWN',        'Alabang Town Center',          'Muntinlupa',   'ACTIVE', NOW(), NOW()),
('FILINVEST_CITY',      'Filinvest City',               'Muntinlupa',   'ACTIVE', NOW(), NOW()),
('MADRIGAL_BIZ',        'Madrigal Business Park',       'Muntinlupa',   'ACTIVE', NOW(), NOW()),
('SOUTHMALL',           'SM Southmall / Festival',      'Las Piñas',    'ACTIVE', NOW(), NOW()),
('BFRV',                'BF Homes / BF Resort Village', 'Las Piñas',    'ACTIVE', NOW(), NOW()),

-- Parañaque
('SUCAT_INTERCHANGE',   'Sucat Interchange (C5/SLEX)',  'Parañaque',    'ACTIVE', NOW(), NOW()),
('BICUTAN_INTERCHANGE', 'Bicutan Interchange',          'Parañaque',    'ACTIVE', NOW(), NOW()),
('SM_BF',               'SM BF Parañaque',              'Parañaque',    'ACTIVE', NOW(), NOW()),
('COASTAL_MALL',        'Coastal Mall / SM MOA Area',   'Parañaque',    'ACTIVE', NOW(), NOW()),

-- Las Piñas / C5
('NAGA_RD_C5',          'Naga Road / C5 Intersection',  'Las Piñas',    'ACTIVE', NOW(), NOW()),
('GATCHALIAN',          'Gatchalian Ave',               'Las Piñas',    'ACTIVE', NOW(), NOW()),

-- Taguig
('FTI',                 'FTI Complex',                  'Taguig',       'ACTIVE', NOW(), NOW()),
('WESTERN_BICUTAN',     'Western Bicutan',              'Taguig',       'ACTIVE', NOW(), NOW()),
('BGC_HIGH_STREET',     'BGC High Street',              'Taguig',       'ACTIVE', NOW(), NOW()),
('BGC_9TH_AVE',         'BGC 9th Avenue',               'Taguig',       'ACTIVE', NOW(), NOW()),
('BGC_MCKINLEY',        'McKinley Parkway / Uptown',    'Taguig',       'ACTIVE', NOW(), NOW()),
('SM_AURA',             'SM Aura / BGC',                'Taguig',       'ACTIVE', NOW(), NOW()),
('FINANCE_CENTER',      'Finance Center (26th St)',     'Taguig',       'ACTIVE', NOW(), NOW()),
('ICON_RCBC',           'ICON / RCBC Plaza',            'Taguig',       'ACTIVE', NOW(), NOW()),
('MCDO_FORUM',          'McDo Forum (BGC)',             'Taguig',       'ACTIVE', NOW(), NOW()),

-- Makati
('AYALA_MRT',           'Ayala MRT Station',            'Makati',       'ACTIVE', NOW(), NOW()),
('GLORIETTA',           'Glorietta Mall',               'Makati',       'ACTIVE', NOW(), NOW()),
('GREENBELT',           'Greenbelt',                    'Makati',       'ACTIVE', NOW(), NOW()),
('BUENDIA_MRT',         'Buendia MRT Station',          'Makati',       'ACTIVE', NOW(), NOW()),
('MAGALLANES',          'Magallanes Interchange',       'Makati',       'ACTIVE', NOW(), NOW()),
('CIRCUIT_MAKATI',      'Circuit Makati',               'Makati',       'ACTIVE', NOW(), NOW()),

-- Common route landmarks (from actual GC posts)
('CITI_PLAZA',          'Citi Plaza BGC',               'Taguig',       'ACTIVE', NOW(), NOW()),
('GLOBE_BUS_STOP',      'Globe Bus Stop BGC',           'Taguig',       'ACTIVE', NOW(), NOW()),
('GENTLE_MONSTER',      'Gentle Monster BGC',           'Taguig',       'ACTIVE', NOW(), NOW()),
('UPTOWN_MALL',         'Uptown Mall BGC',              'Taguig',       'ACTIVE', NOW(), NOW()),
('GRAND_HYATT_BGC',     'Grand Hyatt BGC',              'Taguig',       'ACTIVE', NOW(), NOW());
