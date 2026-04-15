-- V11__seed_hubs_comprehensive.sql
-- Comprehensive hub seed for SouthPool — South MM to BGC/Makati corridor
-- Replaces V2 seed (31 hubs) with full coverage (~143 hubs)
-- Cities: Las Pinas, Paranaque, Muntinlupa, Taguig/BGC, Makati, Pasay, Pateros

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM hubs WHERE suggested_by IS NULL;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO hubs (code, name, area, status, created_at, updated_at) VALUES

-- ─────────────────────────────────────────────────────────
-- LAS PINAS
-- ─────────────────────────────────────────────────────────
('SM_SOUTHMALL',              'SM Southmall',                          'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('ROBINSONS_LP',              'Robinsons Place Las Pinas',             'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('EVIA_LIFESTYLE',            'Evia Lifestyle Center',                 'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('PUREGOLD_LP',               'Puregold Las Pinas (Alabang-Zapote)',   'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('ALABANG_ZAPOTE_LP',         'Alabang-Zapote Road (Las Pinas)',       'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('NAGA_ROAD_C5',              'Naga Road / C5 Extension',              'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('GATCHALIAN_AVE',            'Gatchalian Avenue',                     'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('MARCOS_ALVAREZ',            'Marcos Alvarez Avenue',                 'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('CAA_ROAD',                  'CAA Road / BF International Village',   'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('ZAPOTE_BOUNDARY',           'Zapote (Las Pinas-Bacoor Boundary)',    'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('MOONWALK_CASIMIRO',         'Moonwalk / Casimiro Area',              'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('BF_RESORT_VILLAGE',         'BF Resort Village (BFRV)',              'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('BF_HOMES_LP',               'BF Homes Las Pinas',                    'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('PILAR_VILLAGE',             'Pilar Village',                         'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('PAMPLONA',                  'Pamplona',                              'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('ALMANZA',                   'Almanza (Uno / Dos)',                   'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('TALON',                     'Talon Area (Uno / Dos / Tres)',         'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('PULANG_LUPA',               'Pulang Lupa',                           'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('VERDANT_CAMELLA_LP',        'Verdant Acres / Camella Las Pinas',     'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('ELIAS_ALDANA',              'Elias Aldana',                          'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('DANIEL_FAJARDO',            'Daniel Fajardo Area',                   'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('MANUYO',                    'Manuyo Area',                           'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('ILAYA_LP',                  'Ilaya Las Pinas',                       'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('PAMPLONA_TRES',             'Pamplona Tres / Camella Homes',         'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('TALON_SINGKO',              'Talon Singko',                          'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('SAN_ANTONIO_VALLEY',        'San Antonio Valley',                    'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('LAS_PINAS_CITY_HALL',       'Las Pinas City Hall',                   'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),
('PERPETUAL_HELP',            'Perpetual Help Medical Center Area',    'Las Pinas',  'ACTIVE', NOW(6), NOW(6)),

-- ─────────────────────────────────────────────────────────
-- PARANAQUE
-- ─────────────────────────────────────────────────────────
('SM_SUCAT',                  'SM City Sucat',                         'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('SM_BF_PARANAQUE',           'SM BF Paranaque',                       'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('COASTAL_MALL',              'Coastal Mall / Aseana City',            'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('AYALA_MANILA_BAY',          'Ayala Malls Manila Bay',                'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('DUTY_FREE_FIESTA',          'Duty Free / Fiesta Mall',               'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('SUCAT_INTERCHANGE',         'Sucat Interchange (SLEX)',              'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('BICUTAN_INTERCHANGE',       'Bicutan Interchange (SLEX)',            'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('DR_SANTOS_AVE',             'Dr. Santos Avenue / Sucat Road',        'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('QUIRINO_AVE_PARANAQUE',     'Quirino Avenue Paranaque',              'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('MULTINATIONAL_AVE',         'Multinational Avenue',                  'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('WEST_SERVICE_ROAD',         'West Service Road (Paranaque)',         'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('LRT_MIA_ROAD',              'LRT-1 MIA Road Station',               'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('LRT_PITX_STATION',          'LRT-1 PITX / Asia World Station',      'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('LRT_DR_SANTOS',             'LRT-1 Dr. Santos Station (Sucat)',      'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('PITX',                      'PITX (Paranaque Integrated Terminal)',  'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('SUCAT_ROTONDA',             'Sucat Rotonda',                         'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('BICUTAN_TERMINAL',          'Bicutan Terminal',                      'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('OLIVAREZ_AREA',             'Olivarez Area',                         'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('BF_HOMES_PARANAQUE',        'BF Homes Paranaque',                    'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('BETTER_LIVING',             'Better Living Subdivision',             'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('DON_BOSCO_PARANAQUE',       'Don Bosco Paranaque',                   'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('MULTINATIONAL_VILLAGE',     'Multinational Village',                 'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('SUN_VALLEY',                'Sun Valley Subdivision',                'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('TAMBO',                     'Tambo Paranaque',                       'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('MERVILLE',                  'Merville Subdivision',                  'Paranaque',  'ACTIVE', NOW(6), NOW(6)),
('LA_HUERTA',                 'La Huerta Paranaque',                   'Paranaque',  'ACTIVE', NOW(6), NOW(6)),

-- ─────────────────────────────────────────────────────────
-- MUNTINLUPA
-- ─────────────────────────────────────────────────────────
('ALABANG_TOWN_CENTER',       'Alabang Town Center (ATC)',             'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('FESTIVAL_SUPERMALL',        'Festival Supermall',                    'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('STARMALL_ALABANG',          'Starmall Alabang / VTX',                'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('SM_CENTER_MUNTINLUPA',      'SM Center Muntinlupa',                  'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('WESTGATE_CENTER',           'Westgate Center Alabang',               'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('MOLITO',                    'Molito Lifestyle Center',               'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('EVIA_MALL',                 'Evia Mall (Daang Hari)',                'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('FILINVEST_CITY',            'Filinvest City / Northgate',            'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('MADRIGAL_BUSINESS_PARK',    'Madrigal Business Park',                'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('ASIAN_HOSPITAL',            'Asian Hospital Area (Civic Drive)',     'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('ALABANG_ZAPOTE_MUNTINLUPA', 'Alabang-Zapote Road (Muntinlupa)',      'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('DAANG_HARI',                'Daang Hari Road',                       'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('ALABANG_EXIT_SLEX',         'Alabang Exit / SLEX',                   'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('SOUTH_STATION_ALABANG',     'Alabang South Station',                 'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('AYALA_ALABANG_VILLAGE',     'Ayala Alabang Village',                 'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('ALABANG_HILLS',             'Alabang Hills Village',                 'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('SUSANA_HEIGHTS',            'Susana Heights',                        'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('KATARUNGAN_VILLAGE',        'Katarungan Village',                    'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('PORTOFINO_BRITTANY',        'Portofino / Brittany',                  'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('VICTORIA_HOMES',            'Victoria Homes Muntinlupa',             'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('SOLDIERS_HILLS',            'Soldiers Hills Village',                'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),
('MUNTINLUPA_CITY_HALL',      'Muntinlupa City Hall',                  'Muntinlupa', 'ACTIVE', NOW(6), NOW(6)),

-- ─────────────────────────────────────────────────────────
-- TAGUIG / BGC
-- ─────────────────────────────────────────────────────────
('BGC_HIGH_STREET',           'BGC High Street',                       'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('BGC_5TH_AVE',               'BGC 5th Avenue',                        'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('BGC_9TH_AVE',               'BGC 9th Avenue',                        'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('BGC_32ND_STREET',           'BGC 32nd Street',                       'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('MARKET_MARKET',             'Market! Market!',                       'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('SERENDRA',                  'Serendra / Bonifacio Stopover',         'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('SM_AURA',                   'SM Aura Premier',                       'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('UPTOWN_MALL',               'Uptown Mall / Uptown Bonifacio',        'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('ONE_BGC',                   'One Bonifacio High Street',             'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('GRAND_HYATT_BGC',           'Grand Hyatt BGC / Finance Center',      'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('ST_LUKES_BGC',              'St. Luke''s Medical Center BGC',        'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('BGC_BUS_TERMINAL',          'BGC Bus Terminal (EDSA-McKinley)',      'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('MCKINLEY_HILL',             'McKinley Hill',                         'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('MCKINLEY_WEST',             'McKinley West',                         'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('VENICE_MCKINLEY',           'Venice Grand Canal Mall',               'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('FORT_STRIP',                'The Fort Strip / Enderun',              'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('AFP_HOUSING_GATE3',         'AFP Housing / Gate 3',                  'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('FTI_COMPLEX',               'FTI Complex',                           'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('BICUTAN_MARKET_MARKET',     'Bicutan (Market! Market! side)',        'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('HAGONOY_WSR',               'Hagonoy Road / West Service Road',      'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('ARCA_SOUTH',                'Arca South',                            'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('KALAYAAN_FLYOVER',          'Kalayaan Flyover / C5',                 'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('TAGUIG_CITY_HALL',          'Taguig City Hall',                      'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('LOWER_BICUTAN',             'Lower Bicutan',                         'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('UPPER_BICUTAN',             'Upper Bicutan',                         'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('SIGNAL_VILLAGE',            'Signal Village Taguig',                 'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('WESTERN_BICUTAN',           'Western Bicutan',                       'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('SOUTH_CEMBO',               'South Cembo / EMBO Area',               'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('NAPINDAN_TAGUIG',           'Napindan Taguig',                       'Taguig',     'ACTIVE', NOW(6), NOW(6)),
('USUSAN',                    'Ususan Taguig',                         'Taguig',     'ACTIVE', NOW(6), NOW(6)),

-- ─────────────────────────────────────────────────────────
-- MAKATI
-- ─────────────────────────────────────────────────────────
('AYALA_MRT',                 'Ayala MRT Station',                     'Makati',     'ACTIVE', NOW(6), NOW(6)),
('GLORIETTA',                 'Glorietta',                             'Makati',     'ACTIVE', NOW(6), NOW(6)),
('GREENBELT',                 'Greenbelt',                             'Makati',     'ACTIVE', NOW(6), NOW(6)),
('ONE_AYALA_TERMINAL',        'One Ayala Terminal',                    'Makati',     'ACTIVE', NOW(6), NOW(6)),
('LANDMARK_MAKATI',           'Landmark Makati',                       'Makati',     'ACTIVE', NOW(6), NOW(6)),
('ROCKWELL',                  'Rockwell Center',                       'Makati',     'ACTIVE', NOW(6), NOW(6)),
('CENTURY_CITY',              'Century City Mall',                     'Makati',     'ACTIVE', NOW(6), NOW(6)),
('CIRCUIT_MAKATI',            'Circuit Makati',                        'Makati',     'ACTIVE', NOW(6), NOW(6)),
('BUENDIA_MRT',               'Buendia MRT Station',                   'Makati',     'ACTIVE', NOW(6), NOW(6)),
('GUADALUPE_MRT',             'Guadalupe MRT Station',                 'Makati',     'ACTIVE', NOW(6), NOW(6)),
('MAGALLANES_MRT',            'Magallanes MRT / Interchange',          'Makati',     'ACTIVE', NOW(6), NOW(6)),
('SALCEDO_VILLAGE',           'Salcedo Village Makati',                'Makati',     'ACTIVE', NOW(6), NOW(6)),
('LEGASPI_VILLAGE',           'Legaspi Village Makati',                'Makati',     'ACTIVE', NOW(6), NOW(6)),
('BEL_AIR',                   'Bel-Air Village Makati',                'Makati',     'ACTIVE', NOW(6), NOW(6)),
('SAN_LORENZO',               'San Lorenzo Village Makati',            'Makati',     'ACTIVE', NOW(6), NOW(6)),
('PIO_DEL_PILAR',             'Pio del Pilar Makati',                  'Makati',     'ACTIVE', NOW(6), NOW(6)),
('PALANAN',                   'Palanan Makati',                        'Makati',     'ACTIVE', NOW(6), NOW(6)),
('OLYMPIA_TEJEROS',           'Olympia / Tejeros Makati',              'Makati',     'ACTIVE', NOW(6), NOW(6)),
('DASMARINAS_VILLAGE',        'Dasmarinas Village / Forbes Park',      'Makati',     'ACTIVE', NOW(6), NOW(6)),
('ALPHALAND_ARNAIZ',          'Alphaland / Arnaiz Avenue',             'Makati',     'ACTIVE', NOW(6), NOW(6)),
('GIL_PUYAT_MAKATI',          'Gil Puyat Avenue Makati',               'Makati',     'ACTIVE', NOW(6), NOW(6)),
('BUENDIA_MAKATI',            'Buendia / EDSA Makati',                 'Makati',     'ACTIVE', NOW(6), NOW(6)),

-- ─────────────────────────────────────────────────────────
-- PASAY
-- ─────────────────────────────────────────────────────────
('SM_MOA',                    'SM Mall of Asia (MOA)',                  'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('MOA_ARENA',                 'MOA Arena / Concert Grounds',           'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('ENTERTAINMENT_CITY',        'Entertainment City (Solaire/Okada)',    'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('AYALA_MOA_AREA',            'Ayala Malls Manila Bay (Pasay side)',   'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('EDSA_TAFT_PASAY',           'EDSA Taft / Pasay Rotonda',             'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('LRT_GIL_PUYAT',             'LRT-1 Gil Puyat Station',              'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('LRT_BACLARAN',              'LRT-1 Baclaran Station',               'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('NAIA_TERMINAL',             'NAIA Terminal Area',                    'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('MIA_ROAD_PASAY',            'MIA Road / Airport Road',              'Pasay',      'ACTIVE', NOW(6), NOW(6)),
('VILLAMOR_AIRBASE',          'Villamor Air Base Area',               'Pasay',      'ACTIVE', NOW(6), NOW(6)),

-- ─────────────────────────────────────────────────────────
-- PATEROS
-- ─────────────────────────────────────────────────────────
('PATEROS_TOWN_CENTER',       'Pateros Town Center / Municipal Hall',  'Pateros',    'ACTIVE', NOW(6), NOW(6)),
('PATEROS_MARKET',            'Pateros Market (Wawa)',                 'Pateros',    'ACTIVE', NOW(6), NOW(6)),
('NAPINDAN_PATEROS',          'Napindan Channel Area (Pateros)',       'Pateros',    'ACTIVE', NOW(6), NOW(6)),
('SAINT_MARTHA_PATEROS',      'Saint Martha Parish Area Pateros',      'Pateros',    'ACTIVE', NOW(6), NOW(6)),
('PATEROS_KAPITOLYO_BOUND',   'Pateros / Kapitolyo Boundary',         'Pateros',    'ACTIVE', NOW(6), NOW(6));