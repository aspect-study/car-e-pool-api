-- V11__seed_hubs_comprehensive.sql
-- Comprehensive hub seed for SouthPool — South MM to BGC/Makati corridor
-- Replaces V2 seed (31 hubs) with full coverage (~133 hubs)
-- Cities: Las Piñas, Parañaque, Muntinlupa, Taguig/BGC, Makati, Pasay, Pateros

-- ─────────────────────────────────────────────────────────
-- Step 1: Remove all system-seeded hubs (suggested_by IS NULL)
-- User-suggested hubs (suggested_by IS NOT NULL) are preserved
-- ─────────────────────────────────────────────────────────
DELETE FROM hubs WHERE suggested_by IS NULL;

-- ─────────────────────────────────────────────────────────
-- LAS PIÑAS (28 hubs)
-- ─────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, status) VALUES
-- Malls & Commercial
('SM_SOUTHMALL',           'SM Southmall',                        'ACTIVE'),
('ROBINSONS_LP',           'Robinsons Place Las Piñas',            'ACTIVE'),
('EVIA_LIFESTYLE',         'Evia Lifestyle Center',                'ACTIVE'),
('PUREGOLD_LP',            'Puregold Las Piñas (Alabang-Zapote)',  'ACTIVE'),

-- Major Roads & Intersections
('ALABANG_ZAPOTE_LP',      'Alabang-Zapote Road (Las Piñas)',      'ACTIVE'),
('NAGA_ROAD_C5',           'Naga Road / C5 Extension',             'ACTIVE'),
('GATCHALIAN_AVE',         'Gatchalian Avenue',                    'ACTIVE'),
('MARCOS_ALVAREZ',         'Marcos Alvarez Avenue',                'ACTIVE'),
('CAA_ROAD',               'CAA Road / BF International Village',  'ACTIVE'),
('ZAPOTE_BOUNDARY',        'Zapote (Las Piñas-Bacoor Boundary)',    'ACTIVE'),
('MOONWALK_CASIMIRO',      'Moonwalk / Casimiro Area',             'ACTIVE'),

-- Subdivisions & Villages
('BF_RESORT_VILLAGE',      'BF Resort Village (BFRV)',             'ACTIVE'),
('BF_HOMES_LP',            'BF Homes Las Piñas',                   'ACTIVE'),
('PILAR_VILLAGE',          'Pilar Village',                        'ACTIVE'),
('PAMPLONA',               'Pamplona',                             'ACTIVE'),
('ALMANZA',                'Almanza (Uno / Dos)',                   'ACTIVE'),
('TALON',                  'Talon Area (Uno / Dos / Tres)',         'ACTIVE'),
('PULANG_LUPA',            'Pulang Lupa',                          'ACTIVE'),
('VERDANT_CAMELLA_LP',     'Verdant Acres / Camella Las Piñas',    'ACTIVE'),
('ELIAS_ALDANA',           'Elias Aldana',                         'ACTIVE'),
('DANIEL_FAJARDO',         'Daniel Fajardo Area',                  'ACTIVE'),
('MANUYO',                 'Manuyo Area',                          'ACTIVE'),
('ILAYA_LP',               'Ilaya Las Piñas',                      'ACTIVE'),
('PAMPLONA_TRES',          'Pamplona Tres / Camella Homes',        'ACTIVE'),
('TALON_SINGKO',           'Talon Singko',                         'ACTIVE'),
('SAN_ANTONIO_VALLEY',     'San Antonio Valley',                   'ACTIVE'),
('LAS_PINAS_CITY_HALL',    'Las Piñas City Hall',                  'ACTIVE'),
('PERPETUAL_HELP',         'Perpetual Help Medical Center Area',   'ACTIVE'),

-- ─────────────────────────────────────────────────────────
-- PARAÑAQUE (26 hubs)
-- ─────────────────────────────────────────────────────────

-- Malls & Commercial
('SM_SUCAT',               'SM City Sucat',                        'ACTIVE'),
('SM_BF_PARANAQUE',        'SM BF Parañaque',                      'ACTIVE'),
('COASTAL_MALL',           'Coastal Mall / Aseana City',           'ACTIVE'),
('AYALA_MANILA_BAY',       'Ayala Malls Manila Bay',               'ACTIVE'),
('DUTY_FREE_FIESTA',       'Duty Free / Fiesta Mall',              'ACTIVE'),

-- Major Roads & Interchanges
('SUCAT_INTERCHANGE',      'Sucat Interchange (SLEX)',             'ACTIVE'),
('BICUTAN_INTERCHANGE',    'Bicutan Interchange (SLEX)',           'ACTIVE'),
('DR_SANTOS_AVE',          'Dr. Santos Avenue / Sucat Road',       'ACTIVE'),
('QUIRINO_AVE_PARANAQUE',  'Quirino Avenue Parañaque',             'ACTIVE'),
('MULTINATIONAL_AVE',      'Multinational Avenue',                 'ACTIVE'),
('WEST_SERVICE_ROAD',      'West Service Road (Parañaque)',        'ACTIVE'),

-- LRT Stations
('LRT_MIA_ROAD',           'LRT-1 MIA Road Station',              'ACTIVE'),
('LRT_PITX_STATION',       'LRT-1 PITX / Asia World Station',     'ACTIVE'),
('LRT_DR_SANTOS',          'LRT-1 Dr. Santos Station (Sucat)',     'ACTIVE'),

-- Transport Hubs
('PITX',                   'PITX (Parañaque Integrated Terminal)', 'ACTIVE'),
('SUCAT_ROTONDA',          'Sucat Rotonda',                        'ACTIVE'),
('BICUTAN_TERMINAL',       'Bicutan Terminal',                     'ACTIVE'),
('OLIVAREZ_AREA',          'Olivarez Area',                        'ACTIVE'),

-- Subdivisions & Villages
('BF_HOMES_PARANAQUE',     'BF Homes Parañaque',                   'ACTIVE'),
('BETTER_LIVING',          'Better Living Subdivision',            'ACTIVE'),
('DON_BOSCO_PARANAQUE',    'Don Bosco Parañaque',                  'ACTIVE'),
('MULTINATIONAL_VILLAGE',  'Multinational Village',                'ACTIVE'),
('SUN_VALLEY',             'Sun Valley Subdivision',               'ACTIVE'),
('TAMBO',                  'Tambo Parañaque',                      'ACTIVE'),
('MERVILLE',               'Merville Subdivision',                 'ACTIVE'),
('LA_HUERTA',              'La Huerta Parañaque',                  'ACTIVE'),

-- ─────────────────────────────────────────────────────────
-- MUNTINLUPA (22 hubs)
-- ─────────────────────────────────────────────────────────

-- Malls & Commercial
('ALABANG_TOWN_CENTER',    'Alabang Town Center (ATC)',            'ACTIVE'),
('FESTIVAL_SUPERMALL',     'Festival Supermall',                   'ACTIVE'),
('STARMALL_ALABANG',       'Starmall Alabang / VTX',               'ACTIVE'),
('SM_CENTER_MUNTINLUPA',   'SM Center Muntinlupa',                 'ACTIVE'),
('WESTGATE_CENTER',        'Westgate Center Alabang',              'ACTIVE'),
('MOLITO',                 'Molito Lifestyle Center',              'ACTIVE'),
('EVIA_MALL',              'Evia Mall (Daang Hari)',               'ACTIVE'),

-- Business Districts
('FILINVEST_CITY',         'Filinvest City / Northgate',           'ACTIVE'),
('MADRIGAL_BUSINESS_PARK', 'Madrigal Business Park',               'ACTIVE'),
('ASIAN_HOSPITAL',         'Asian Hospital Area (Civic Drive)',    'ACTIVE'),

-- Major Roads
('ALABANG_ZAPOTE_MUNTINLUPA', 'Alabang-Zapote Road (Muntinlupa)', 'ACTIVE'),
('DAANG_HARI',             'Daang Hari Road',                      'ACTIVE'),
('ALABANG_EXIT_SLEX',      'Alabang Exit / SLEX',                  'ACTIVE'),
('SOUTH_STATION_ALABANG',  'Alabang South Station',                'ACTIVE'),

-- Subdivisions & Villages
('AYALA_ALABANG_VILLAGE',  'Ayala Alabang Village',                'ACTIVE'),
('ALABANG_HILLS',          'Alabang Hills Village',                'ACTIVE'),
('SUSANA_HEIGHTS',         'Susana Heights',                       'ACTIVE'),
('KATARUNGAN_VILLAGE',     'Katarungan Village',                   'ACTIVE'),
('PORTOFINO_BRITTANY',     'Portofino / Brittany',                 'ACTIVE'),
('VICTORIA_HOMES',         'Victoria Homes Muntinlupa',            'ACTIVE'),
('SOLDIERS_HILLS',         'Soldiers Hills Village',               'ACTIVE'),
('MUNTINLUPA_CITY_HALL',   'Muntinlupa City Hall',                 'ACTIVE'),

-- ─────────────────────────────────────────────────────────
-- TAGUIG / BGC (30 hubs)
-- ─────────────────────────────────────────────────────────

-- BGC Core
('BGC_HIGH_STREET',        'BGC High Street',                      'ACTIVE'),
('BGC_5TH_AVE',            'BGC 5th Avenue',                       'ACTIVE'),
('BGC_9TH_AVE',            'BGC 9th Avenue',                       'ACTIVE'),
('BGC_32ND_STREET',        'BGC 32nd Street',                      'ACTIVE'),
('MARKET_MARKET',          'Market! Market!',                      'ACTIVE'),
('SERENDRA',               'Serendra / Bonifacio Stopover',        'ACTIVE'),
('SM_AURA',                'SM Aura Premier',                      'ACTIVE'),
('UPTOWN_MALL',            'Uptown Mall / Uptown Bonifacio',       'ACTIVE'),
('ONE_BGC',                'One Bonifacio High Street',            'ACTIVE'),
('GRAND_HYATT_BGC',        'Grand Hyatt BGC / Finance Center',     'ACTIVE'),
('ST_LUKES_BGC',           'St. Luke's Medical Center BGC',        'ACTIVE'),
('BGC_BUS_TERMINAL',       'BGC Bus Terminal (EDSA-McKinley)',     'ACTIVE'),

-- McKinley / Fort Area
('MCKINLEY_HILL',          'McKinley Hill',                        'ACTIVE'),
('MCKINLEY_WEST',          'McKinley West',                        'ACTIVE'),
('VENICE_MCKINLEY',        'Venice Grand Canal Mall',              'ACTIVE'),
('FORT_STRIP',             'The Fort Strip / Enderun',             'ACTIVE'),
('AFP_HOUSING_GATE3',      'AFP Housing / Gate 3',                 'ACTIVE'),

-- FTI / Bicutan / South Taguig
('FTI_COMPLEX',            'FTI Complex',                          'ACTIVE'),
('BICUTAN_MARKET_MARKET',  'Bicutan (Market! Market! side)',       'ACTIVE'),
('HAGONOY_WSR',            'Hagonoy Road / West Service Road',     'ACTIVE'),
('ARCA_SOUTH',             'Arca South',                           'ACTIVE'),
('KALAYAAN_FLYOVER',       'Kalayaan Flyover / C5',                'ACTIVE'),

-- Taguig Proper
('TAGUIG_CITY_HALL',       'Taguig City Hall',                     'ACTIVE'),
('LOWER_BICUTAN',          'Lower Bicutan',                        'ACTIVE'),
('UPPER_BICUTAN',          'Upper Bicutan',                        'ACTIVE'),
('SIGNAL_VILLAGE',         'Signal Village Taguig',                'ACTIVE'),
('WESTERN_BICUTAN',        'Western Bicutan',                      'ACTIVE'),
('SOUTH_CEMBO',            'South Cembo / EMBO Area',              'ACTIVE'),
('NAPINDAN_TAGUIG',        'Napindan Taguig',                      'ACTIVE'),
('USUSAN',                 'Ususan Taguig',                        'ACTIVE'),

-- ─────────────────────────────────────────────────────────
-- MAKATI (22 hubs)
-- ─────────────────────────────────────────────────────────

-- Ayala Center & CBD
('AYALA_MRT',              'Ayala MRT Station',                    'ACTIVE'),
('GLORIETTA',              'Glorietta',                            'ACTIVE'),
('GREENBELT',              'Greenbelt',                            'ACTIVE'),
('ONE_AYALA_TERMINAL',     'One Ayala Terminal',                   'ACTIVE'),
('LANDMARK_MAKATI',        'Landmark Makati',                      'ACTIVE'),
('ROCKWELL',               'Rockwell Center',                      'ACTIVE'),
('CENTURY_CITY',           'Century City Mall',                    'ACTIVE'),
('CIRCUIT_MAKATI',         'Circuit Makati',                       'ACTIVE'),

-- MRT Stations
('BUENDIA_MRT',            'Buendia MRT Station',                  'ACTIVE'),
('GUADALUPE_MRT',          'Guadalupe MRT Station',                'ACTIVE'),
('MAGALLANES_MRT',         'Magallanes MRT / Interchange',         'ACTIVE'),

-- Business Districts & Villages
('SALCEDO_VILLAGE',        'Salcedo Village Makati',               'ACTIVE'),
('LEGASPI_VILLAGE',        'Legaspi Village Makati',               'ACTIVE'),
('BEL_AIR',                'Bel-Air Village Makati',               'ACTIVE'),
('SAN_LORENZO',            'San Lorenzo Village Makati',           'ACTIVE'),
('PIO_DEL_PILAR',          'Pio del Pilar Makati',                 'ACTIVE'),
('PALANAN',                'Palanan Makati',                       'ACTIVE'),
('OLYMPIA_TEJEROS',        'Olympia / Tejeros Makati',             'ACTIVE'),
('DASMARINAS_VILLAGE',     'Dasmarinas Village / Forbes Park',     'ACTIVE'),
('ALPHALAND_ARNAIZ',       'Alphaland / Arnaiz Avenue',            'ACTIVE'),
('GIL_PUYAT_MAKATI',       'Gil Puyat Avenue Makati',             'ACTIVE'),
('BUENDIA_MAKATI',         'Buendia / EDSA Makati',               'ACTIVE'),

-- ─────────────────────────────────────────────────────────
-- PASAY (10 hubs)
-- ─────────────────────────────────────────────────────────
('SM_MOA',                 'SM Mall of Asia (MOA)',                'ACTIVE'),
('MOA_ARENA',              'MOA Arena / Concert Grounds',          'ACTIVE'),
('ENTERTAINMENT_CITY',     'Entertainment City (Solaire/Okada)',   'ACTIVE'),
('AYALA_MOA_AREA',         'Ayala Malls Manila Bay (Pasay side)',  'ACTIVE'),
('EDSA_TAFT_PASAY',        'EDSA Taft / Pasay Rotonda',            'ACTIVE'),
('LRT_GIL_PUYAT',          'LRT-1 Gil Puyat Station',             'ACTIVE'),
('LRT_BACLARAN',           'LRT-1 Baclaran Station',              'ACTIVE'),
('NAIA_TERMINAL',          'NAIA Terminal Area',                   'ACTIVE'),
('MIA_ROAD_PASAY',         'MIA Road / Airport Road',             'ACTIVE'),
('VILLAMOR_AIRBASE',       'Villamor Air Base Area',              'ACTIVE'),

-- ─────────────────────────────────────────────────────────
-- PATEROS (5 hubs)
-- ─────────────────────────────────────────────────────────
('PATEROS_TOWN_CENTER',    'Pateros Town Center / Municipal Hall', 'ACTIVE'),
('PATEROS_MARKET',         'Pateros Market (Wawa)',                'ACTIVE'),
('NAPINDAN_PATEROS',       'Napindan Channel Area (Pateros)',      'ACTIVE'),
('SAINT_MARTHA_PATEROS',   'Saint Martha Parish Area Pateros',     'ACTIVE'),
('PATEROS_KAPITOLYO_BOUND','Pateros / Kapitolyo Boundary',         'ACTIVE');

