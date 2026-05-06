-- V34__add_new_hubs.sql
-- Adds new community-requested hubs across Las Pinas, Paranaque, Muntinlupa,
-- Taguig, Makati, Pasay, and Pateros areas.
-- All hubs are ACTIVE and system-generated (suggested_by = NULL).

SET NAMES utf8mb4;

-- ── Las Piñas ─────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_ABEL_NOSCE', 'Abel Nosce St.', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_ANYTIME_VEAL', 'Anytime Veal Burger', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_CAMELLA_SPRINGVILLE', 'Camella Springville Molino', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_CHOWKING_TIMES', 'Chowking Times (Mcdo Times area)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_CITTADELLA', 'Cittadella (Subdivision)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_DBP_BRANCH', 'DBP (Development Bank of the Philippines)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_DONA_MANUELA', 'Doña Manuela Subdivision (RFC area)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_GOLDEN_HAVEN', 'Golden Haven Subdivision', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_GOODYEAR_AZR', 'Goodyear Alabang-Zapote Road', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_JOLLIBEE_TIMES', 'Jollibee Times', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_LPDH_SOUTHVILLE', 'LPDH Southville', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MANG_INASAL_RFC', 'Mang Inasal RFC', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MANILA_TIMES_SUBD', 'Manila Times (Subdivision)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MANUELA_GENERAL', 'Manuela (Subdivision)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MAPAYAPA_VILLAGE', 'Mapayapa Village', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MCDO_HILL', 'Mcdo Hill (Pilar Village area)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_METROCOR_B', 'Metrocor B', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MINISTOP_NAGA', 'Ministop Naga Road (Vergonville area)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MOLINO_ROAD', 'Molino Road (Bacoor/LP border)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MOONWALK_LPS', 'Moonwalk (Las Piñas side)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_MULTI_LP', 'Multi (Multinational Village - LP side)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_PHILAM_VILLAGE', 'Philam (Philamlife Village)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_PMMS', 'PMMS', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_PULANGLUPA_GENERAL', 'Pulanglupa (Area)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_PUREGOLD_NAGA', 'Puregold Naga Road', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_RFC', 'RFC (Residencia Familia de Concepcion)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_RICHMOND_SHELL', 'Richmond / Shell Naga Road', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_SM_CENTER_PAMPLONA', 'SM Center Las Piñas (Pamplona)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_SOLDIERS_HILLS_2', 'Soldiers Hills II', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_SOUTHLAND_BAKERY', 'Southland Bakery', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_SOUTHVILLE_SUBD', 'Southville (Subdivision)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_TIMES_SUBD', 'Times (Subdivision)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_TS_CRUZ_SUBD', 'TS Cruz (Subdivision)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_UNIOIL_MULTI', 'Unioil (near Multinational/East Service Road)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_UPHSD_CAMPS', 'UPHSD Campus (Perpetual Help)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_VERGONVILLE', 'Vergonville (Vergon)', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_VILLA_ISABELITA', 'Villa Isabelita Subd., Naga Road', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('LP_VILLAR_SIPAG_C', 'Villar Sipag Center', 'Las Pinas', NULL, 'ACTIVE', NOW(6), NOW(6));

-- ── Parañaque ─────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_CAA_BF_SPECIFIC', 'CAA Road / BF International (Specific segment)', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_C5_SHELL', 'C5 Extension Shell Station', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_CLARMEN', 'Clarmen (Villanueva Village)', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_ELIZALDE_BF', 'Elizalde Ave / BF Homes', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_FRANCE_ST', 'France St / Better Living Phase 3', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_MOONWALK_TALAB', 'Moonwalk Talaba', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_MULTINATIONAL_SPEC', 'Multinational Avenue (specific)', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_SUCATR_SAMPAGUITA', 'Sucat Road / Sampaguita (UPS2 Area)', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_UNITED_PARANAQUE', 'United Parañaque (UP1/UP2/UP3)', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PRQ_VALLEY_A2_GATE', 'Valley 2 Gate (San Antonio Valley 2)', 'Paranaque', NULL, 'ACTIVE', NOW(6), NOW(6));

-- ── Muntinlupa ────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_678_COMMERCIAL', '678 (Building/Commercial along Bacoor blvd)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_ALABANG_400', 'Alabang 400 (Subdivision)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_BMAG_WSR', 'B-Mag (Area near WSR/ESR)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_CUPANG_WSR', 'Cupang (Brgy. Cupang / West Service Road)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_CRIMSON_HOTEL', 'Crimson Hotel (Filinvest City)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_D_JESUS_BLVD', 'D. Jesus Blvd (Alabang Hills)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_HONDA_ALABANG', 'Honda Alabang (Dealership)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_LAKEFRONT', 'Lakefront (Presidio/Sucat area)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_NOMO_ALABANG', 'NOMO (Building/Commercial - Alabang side)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MNL_RITM', 'Research Inst. for Tropical Med (RITM)', 'Muntinlupa', NULL, 'ACTIVE', NOW(6), NOW(6));

-- ── Taguig ────────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_AFPOVAI_GATE1', 'AFPOVAI Gate 1 (Bayani Road)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_ARMY_OFFICERS', 'Army Officers Village (PHILARCOM)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_ASIA_WORLD_STN', 'Asia World Station (LRT-1 PITX)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_ECOTOWER', 'Ecotower', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_LAWTON_5TH_AVE', 'Lawton Ave/5th Ave Gate', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_LOWER_BICUTAN', 'Lower Bicutan (Brgy. Lower Bicutan)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_MCKINLEY_HILL_LGATE', 'McKinley Hill Gate / Lawton', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_MCKINLEY_PKWY', 'McKinley Parkway (Aura Exit)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_METROBANK_BGC', 'Metrobank Center (BGC)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_MORGAN_SUITES', 'Morgan Suites / Florence Way (McKinley)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_NAPINDAN_TAGUIG', 'Napindan Taguig (Brgy)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_ONE_MCKINLEY', 'One McKinley Place', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_PACIFIC_PLAZA', 'Pacific Plaza Towers (BGC)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_PHIL_ARMY_VILLAGE', 'Phil Army Officers Village', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_SAN_AGUSTIN', 'San Agustin (Napindan area)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_SIGNAL_VILLAGE', 'Signal Village Taguig (Brgy)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_TENEMENT', 'Tenement (Taguig side)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_UPTOWN_RITZ', 'Uptown Ritz (Condo)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('TGG_WESTERN_BICUTAN', 'Western Bicutan (Brgy)', 'Taguig', NULL, 'ACTIVE', NOW(6), NOW(6));

-- ── Makati ────────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_AYALA_AVE_GENERAL', 'Ayala Avenue (Main Hub)', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_BEL_AIR', 'Bel-Air Village Makati', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_CENTURYT_CITY', 'Century City / Trump Tower', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_JP_RIZAL_REMBO', 'J.P. Rizal Ext (West Rembo)', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_KALSADA_PRC', 'Kalsada St / PRC (Tejeros)', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_KALAYAAN_MAKATI', 'Kalayaan Ave / Makati Ave Junction', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_LANDMARK_SPECIFIC', 'Landmark Makati', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_LEGASPI_VILLAGE', 'Legaspi Village (Washington SyCip)', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_PEMBO_FLOWER', 'Pembo/Flower Farm Area', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_PIO_DEL_PILAR', 'Pio del Pilar (Washington / Arnaiz)', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_PITOGO', 'Pitogo (Brgy. Pitogo - BGC-Makati Border)', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_POBLACION_MARKETP', 'Poblacion Market / P. Burgos', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('MKT_SM_MAKAT', 'SM Makati', 'Makati', NULL, 'ACTIVE', NOW(6), NOW(6));

-- ── Pasay ─────────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PSY_CCP', 'CCP (Cultural Center of the Philippines)', 'Pasay', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PSY_GEN_RODRIGOT', 'Gen. Rodrigo St Hub (Tramo)', 'Pasay', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PSY_MACAPAGAL_BLVDD', 'Macapagal Blvd (Diosdado)', 'Pasay', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PSY_NEWPORT_MALLRS', 'Newport Mall (Resorts World)', 'Pasay', NULL, 'ACTIVE', NOW(6), NOW(6));

-- ── Pateros ───────────────────────────────────────────────────────────────────
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PAT_AGUHO', 'Aguho (Brgy. Aguho)', 'Pateros', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PAT_MAGTANGGOL', 'Magtanggol (Brgy)', 'Pateros', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PAT_MARTIRES', 'Martires (San Jose area)', 'Pateros', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PAT_SAN_VICENTE', 'San Vicente (Brgy)', 'Pateros', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PAT_TABACALERA', 'Tabacalera (Brgy)', 'Pateros', NULL, 'ACTIVE', NOW(6), NOW(6));
INSERT INTO hubs (code, name, area, suggested_by, status, created_at, updated_at) VALUES ('PAT_TUKTUKAN', 'Tuktukan (Boundary area)', 'Pateros', NULL, 'ACTIVE', NOW(6), NOW(6));
