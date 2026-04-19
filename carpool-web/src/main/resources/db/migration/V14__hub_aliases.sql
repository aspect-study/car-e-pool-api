CREATE TABLE hub_aliases (
                             id         BIGINT PRIMARY KEY AUTO_INCREMENT,
                             hub_id     BIGINT NOT NULL,
                             alias      VARCHAR(100) NOT NULL,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_alias_hub FOREIGN KEY (hub_id) REFERENCES hubs(id) ON DELETE CASCADE,
                             CONSTRAINT uq_hub_alias UNIQUE (alias)
);

-- ══════════════════════════════════════════════════════════════════════════════
-- LAS PINAS
-- ══════════════════════════════════════════════════════════════════════════════

-- 32: SM Southmall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (32, 'southmall'),
                                            (32, 'sm south'),
                                            (32, 'south mall'),
                                            (32, 'sm southmall'),
                                            (32, 'southmall lp'),
                                            (32, 'sm lp'),
                                            (32, 'southmol'),
                                            (32, 'southmal');

-- 33: Robinsons Place Las Pinas
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (33, 'robinsons lp'),
                                            (33, 'robinsons las pinas'),
                                            (33, 'robinson lp'),
                                            (33, 'rplp'),
                                            (33, 'robinson las pinas'),
                                            (33, 'robinsons place lp');

-- 34: Evia Lifestyle Center
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (34, 'evia'),
                                            (34, 'evia lp'),
                                            (34, 'evia lifestyle'),
                                            (34, 'evia las pinas'),
                                            (34, 'evia center');

-- 35: Puregold Las Pinas
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (35, 'puregold lp'),
                                            (35, 'puregold las pinas'),
                                            (35, 'puregold alabang zapote'),
                                            (35, 'pg lp');

-- 36: Alabang-Zapote Road Las Pinas
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (36, 'alabang zapote lp'),
                                            (36, 'az road lp'),
                                            (36, 'az las pinas'),
                                            (36, 'alabang zapote road lp');

-- 37: Naga Road / C5 Extension
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (37, 'naga road'),
                                            (37, 'c5 extension'),
                                            (37, 'naga c5'),
                                            (37, 'c5 lp'),
                                            (37, 'naga'),
                                            (37, 'naga lp');

-- 38: Gatchalian Avenue
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (38, 'gatchalian'),
                                            (38, 'gatchalian ave'),
                                            (38, 'gatchalian avenue'),
                                            (38, 'gatchalean');

-- 39: Marcos Alvarez Avenue
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (39, 'marcos alvarez'),
                                            (39, 'ma ave'),
                                            (39, 'marcos alvarez ave'),
                                            (39, 'marcos');

-- 40: CAA Road / BF International Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (40, 'caa road'),
                                            (40, 'bf international'),
                                            (40, 'caa bf'),
                                            (40, 'caa'),
                                            (40, 'bf international village');

-- 41: Zapote (Las Pinas-Bacoor Boundary)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (41, 'zapote'),
                                            (41, 'zapote boundary'),
                                            (41, 'zapote bacoor'),
                                            (41, 'lp bacoor'),
                                            (41, 'las pinas bacoor boundary');

-- 42: Moonwalk / Casimiro Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (42, 'moonwalk'),
                                            (42, 'casimiro'),
                                            (42, 'moonwalk casimiro'),
                                            (42, 'casimiro ave'),
                                            (42, 'moonwalk area');

-- 43: BF Resort Village (BFRV)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (43, 'bfrv'),
                                            (43, 'bf resort'),
                                            (43, 'bf resort village'),
                                            (43, 'bf resort lp'),
                                            (43, 'bfr');

-- 44: BF Homes Las Pinas
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (44, 'bf homes lp'),
                                            (44, 'bf homes las pinas'),
                                            (44, 'bfh lp'),
                                            (44, 'bf lp');

-- 45: Pilar Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (45, 'pilar'),
                                            (45, 'pilar village'),
                                            (45, 'pilar lp');

-- 46: Pamplona
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (46, 'pamplona lp'),
                                            (46, 'pamplona las pinas'),
                                            (46, 'pamplona uno'),
                                            (46, 'pamplona dos');

-- 47: Almanza (Uno / Dos)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (47, 'almanza'),
                                            (47, 'almanza uno'),
                                            (47, 'almanza dos'),
                                            (47, 'almanza lp');

-- 48: Talon Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (48, 'talon'),
                                            (48, 'talon uno'),
                                            (48, 'talon dos'),
                                            (48, 'talon tres'),
                                            (48, 'talon area'),
                                            (48, 'talon lp');

-- 49: Pulang Lupa
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (49, 'pulang lupa'),
                                            (49, 'pulang lupa lp'),
                                            (49, 'pula lupa');

-- 50: Verdant Acres / Camella Las Pinas
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (50, 'verdant'),
                                            (50, 'verdant acres'),
                                            (50, 'camella lp'),
                                            (50, 'camella las pinas'),
                                            (50, 'verdant camella');

-- 51: Elias Aldana
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (51, 'elias aldana'),
                                            (51, 'aldana'),
                                            (51, 'elias');

-- 52: Daniel Fajardo Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (52, 'daniel fajardo'),
                                            (52, 'fajardo'),
                                            (52, 'fajardo lp');

-- 53: Manuyo Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (53, 'manuyo'),
                                            (53, 'manuyo lp');

-- 54: Ilaya Las Pinas
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (54, 'ilaya'),
                                            (54, 'ilaya lp'),
                                            (54, 'ilaya las pinas');

-- 55: Pamplona Tres / Camella Homes
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (55, 'pamplona tres'),
                                            (55, 'camella homes lp'),
                                            (55, 'camella homes'),
                                            (55, 'pam tres');

-- 56: Talon Singko
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (56, 'talon singko'),
                                            (56, 'talon 5'),
                                            (56, 'talon cinco'),
                                            (56, 'singko');

-- 57: San Antonio Valley
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (57, 'san antonio valley'),
                                            (57, 'sav'),
                                            (57, 'san antonio'),
                                            (57, 'san antonio lp');

-- 58: Las Pinas City Hall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (58, 'lp city hall'),
                                            (58, 'las pinas city hall'),
                                            (58, 'lp hall'),
                                            (58, 'city hall lp');

-- 59: Perpetual Help Medical Center Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (59, 'perpetual'),
                                            (59, 'perpetual help'),
                                            (59, 'dlsph'),
                                            (59, 'dlsp'),
                                            (59, 'perpetual hospital'),
                                            (59, 'perpetual medical'),
                                            (59, 'perpetual las pinas');

-- ══════════════════════════════════════════════════════════════════════════════
-- PARANAQUE
-- ══════════════════════════════════════════════════════════════════════════════

-- 60: SM City Sucat
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (60, 'sm sucat'),
                                            (60, 'sucat mall'),
                                            (60, 'sm city sucat'),
                                            (60, 'sucat sm');

-- 61: SM BF Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (61, 'sm bf'),
                                            (61, 'sm bf paranaque'),
                                            (61, 'sm bf para'),
                                            (61, 'bf paranaque sm');

-- 62: Coastal Mall / Aseana City
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (62, 'coastal'),
                                            (62, 'aseana'),
                                            (62, 'coastal mall'),
                                            (62, 'aseana city'),
                                            (62, 'coastal mall paranaque'),
                                            (62, 'd mall coastal');

-- 63: Ayala Malls Manila Bay
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (63, 'ayala manila bay'),
                                            (63, 'manila bay mall'),
                                            (63, 'amb'),
                                            (63, 'ayala mall manila bay'),
                                            (63, 'manila bay ayala');

-- 64: Duty Free / Fiesta Mall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (64, 'duty free'),
                                            (64, 'fiesta mall'),
                                            (64, 'duty free paranaque'),
                                            (64, 'fiesta'),
                                            (64, 'duty free fiesta');

-- 65: Sucat Interchange (SLEX)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (65, 'sucat interchange'),
                                            (65, 'sucat slex'),
                                            (65, 'sucat exit'),
                                            (65, 'slex sucat');

-- 66: Bicutan Interchange (SLEX)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (66, 'bicutan interchange'),
                                            (66, 'bicutan slex'),
                                            (66, 'bicutan exit'),
                                            (66, 'slex bicutan');

-- 67: Dr. Santos Avenue / Sucat Road
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (67, 'dr santos'),
                                            (67, 'sucat road'),
                                            (67, 'dr santos ave'),
                                            (67, 'doc santos'),
                                            (67, 'dr santos paranaque');

-- 68: Quirino Avenue Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (68, 'quirino paranaque'),
                                            (68, 'quirino ave'),
                                            (68, 'quirino avenue'),
                                            (68, 'quirino para');

-- 69: Multinational Avenue
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (69, 'multinational'),
                                            (69, 'multinational ave'),
                                            (69, 'multinational avenue'),
                                            (69, 'multi ave');

-- 70: West Service Road Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (70, 'wsr paranaque'),
                                            (70, 'west service road'),
                                            (70, 'wsr para'),
                                            (70, 'west service road paranaque');

-- 71: LRT-1 MIA Road Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (71, 'lrt mia'),
                                            (71, 'mia road station'),
                                            (71, 'lrt mia road'),
                                            (71, 'mia station');

-- 72: LRT-1 PITX / Asia World Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (72, 'lrt pitx'),
                                            (72, 'asia world station'),
                                            (72, 'lrt asia world'),
                                            (72, 'pitx lrt');

-- 73: LRT-1 Dr. Santos Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (73, 'lrt dr santos'),
                                            (73, 'lrt sucat'),
                                            (73, 'dr santos station'),
                                            (73, 'sucat lrt');

-- 74: PITX
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (74, 'pitx'),
                                            (74, 'paranaque terminal'),
                                            (74, 'paranaque integrated terminal'),
                                            (74, 'pitx terminal'),
                                            (74, 'integrated terminal paranaque');

-- 75: Sucat Rotonda
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (75, 'sucat rotonda'),
                                            (75, 'rotonda sucat'),
                                            (75, 'sucat roundabout');

-- 76: Bicutan Terminal
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (76, 'bicutan terminal'),
                                            (76, 'terminal bicutan'),
                                            (76, 'bicutan bus terminal');

-- 77: Olivarez Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (77, 'olivarez'),
                                            (77, 'olivarez plaza'),
                                            (77, 'olivarez paranaque');

-- 78: BF Homes Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (78, 'bf homes paranaque'),
                                            (78, 'bf paranaque'),
                                            (78, 'bfh paranaque'),
                                            (78, 'bf para');

-- 79: Better Living Subdivision
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (79, 'better living'),
                                            (79, 'better living subd'),
                                            (79, 'better living paranaque'),
                                            (79, 'bls paranaque');

-- 80: Don Bosco Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (80, 'don bosco'),
                                            (80, 'don bosco paranaque'),
                                            (80, 'don bosco para'),
                                            (80, 'db paranaque');

-- 81: Multinational Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (81, 'multinational village'),
                                            (81, 'mnl village'),
                                            (81, 'multi village');

-- 82: Sun Valley Subdivision
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (82, 'sun valley'),
                                            (82, 'sun valley paranaque'),
                                            (82, 'sunvalley');

-- 83: Tambo Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (83, 'tambo'),
                                            (83, 'tambo paranaque'),
                                            (83, 'tambo para');

-- 84: Merville Subdivision
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (84, 'merville'),
                                            (84, 'merville paranaque'),
                                            (84, 'merville subd');

-- 85: La Huerta Paranaque
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (85, 'la huerta'),
                                            (85, 'la huerta paranaque'),
                                            (85, 'lahuerta');

-- ══════════════════════════════════════════════════════════════════════════════
-- MUNTINLUPA
-- ══════════════════════════════════════════════════════════════════════════════

-- 86: Alabang Town Center
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (86, 'atc'),
                                            (86, 'alabang town center'),
                                            (86, 'atc alabang'),
                                            (86, 'town center alabang'),
                                            (86, 'alabang tc');

-- 87: Festival Supermall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (87, 'festival'),
                                            (87, 'festival mall'),
                                            (87, 'festival supermall'),
                                            (87, 'festival alabang'),
                                            (87, 'festival mall alabang'),
                                            (87, 'fest mall');

-- 88: Starmall Alabang / VTX
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (88, 'starmall'),
                                            (88, 'vtx'),
                                            (88, 'starmall alabang'),
                                            (88, 'star mall alabang'),
                                            (88, 'vtx alabang');

-- 89: SM Center Muntinlupa
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (89, 'sm muntinlupa'),
                                            (89, 'sm center muntinlupa'),
                                            (89, 'sm munti'),
                                            (89, 'munti sm');

-- 90: Westgate Center Alabang
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (90, 'westgate'),
                                            (90, 'westgate alabang'),
                                            (90, 'west gate alabang'),
                                            (90, 'westgate center');

-- 91: Molito Lifestyle Center
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (91, 'molito'),
                                            (91, 'molito alabang'),
                                            (91, 'molito lifestyle'),
                                            (91, 'molito center');

-- 92: Evia Mall (Daang Hari)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (92, 'evia mall'),
                                            (92, 'evia daang hari'),
                                            (92, 'evia muntinlupa'),
                                            (92, 'daang hari mall');

-- 93: Filinvest City / Northgate
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (93, 'filinvest'),
                                            (93, 'northgate'),
                                            (93, 'filinvest city'),
                                            (93, 'northgate alabang'),
                                            (93, 'filinvest alabang'),
                                            (93, 'northgate cyberzone');

-- 94: Madrigal Business Park
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (94, 'madrigal'),
                                            (94, 'mbp'),
                                            (94, 'madrigal business park'),
                                            (94, 'madrigal alabang'),
                                            (94, 'mbp alabang');

-- 95: Asian Hospital Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (95, 'asian hospital'),
                                            (95, 'civic drive'),
                                            (95, 'asian hospital alabang'),
                                            (95, 'civic drive alabang'),
                                            (95, 'ahmc'),
                                            (95, 'asian medical');

-- 96: Alabang-Zapote Road Muntinlupa
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (96, 'alabang zapote muntinlupa'),
                                            (96, 'az road muntinlupa'),
                                            (96, 'az munti'),
                                            (96, 'alabang zapote road munti');

-- 97: Daang Hari Road
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (97, 'daang hari'),
                                            (97, 'daang hari road'),
                                            (97, 'daan hari'),
                                            (97, 'daang hari muntinlupa');

-- 98: Alabang Exit / SLEX
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (98, 'alabang exit'),
                                            (98, 'alabang slex'),
                                            (98, 'slex alabang'),
                                            (98, 'alabang exit slex');

-- 99: Alabang South Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (99, 'south station'),
                                            (99, 'alabang south station'),
                                            (99, 'alabang south'),
                                            (99, 'south station alabang');

-- 100: Ayala Alabang Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (100, 'ayala alabang'),
                                            (100, 'aav'),
                                            (100, 'ayala alabang village'),
                                            (100, 'alabang village');

-- 101: Alabang Hills Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (101, 'alabang hills'),
                                            (101, 'alabang hills village'),
                                            (101, 'ahv');

-- 102: Susana Heights
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (102, 'susana heights'),
                                            (102, 'susana heights muntinlupa'),
                                            (102, 'susana');

-- 103: Katarungan Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (103, 'katarungan'),
                                            (103, 'katarungan village'),
                                            (103, 'katarungan muntinlupa');

-- 104: Portofino / Brittany
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (104, 'portofino'),
                                            (104, 'brittany'),
                                            (104, 'portofino daang hari'),
                                            (104, 'brittany alabang'),
                                            (104, 'portofino brittany');

-- 105: Victoria Homes
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (105, 'victoria homes'),
                                            (105, 'victoria homes muntinlupa'),
                                            (105, 'vic homes');

-- 106: Soldiers Hills Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (106, 'soldiers hills'),
                                            (106, 'soldiers hills village'),
                                            (106, 'soldiers hill'),
                                            (106, 'shv muntinlupa');

-- 107: Muntinlupa City Hall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (107, 'muntinlupa city hall'),
                                            (107, 'munti city hall'),
                                            (107, 'city hall muntinlupa'),
                                            (107, 'muntinlupa hall');

-- ══════════════════════════════════════════════════════════════════════════════
-- TAGUIG / BGC
-- ══════════════════════════════════════════════════════════════════════════════

-- 108: BGC High Street
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (108, 'bgc'),
                                            (108, 'high street'),
                                            (108, 'bonifacio high street'),
                                            (108, 'bgc high street'),
                                            (108, 'bonifacio global city'),
                                            (108, 'fort bgc'),
                                            (108, 'the fort bgc'),
                                            (108, 'bgc main'),
                                            (108, 'high street bgc');

-- 109: BGC 5th Avenue
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (109, 'bgc 5th'),
                                            (109, '5th avenue bgc'),
                                            (109, '5th ave bgc'),
                                            (109, 'bgc 5th ave'),
                                            (109, 'fifth avenue bgc');

-- 110: BGC 9th Avenue
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (110, 'bgc 9th'),
                                            (110, '9th avenue bgc'),
                                            (110, '9th ave bgc'),
                                            (110, 'bgc 9th ave'),
                                            (110, 'ninth avenue bgc');

-- 111: BGC 32nd Street
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (111, 'bgc 32nd'),
                                            (111, '32nd street bgc'),
                                            (111, '32nd bgc'),
                                            (111, 'bgc 32nd st'),
                                            (111, 'thirty second bgc');

-- 112: Market! Market!
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (112, 'market market'),
                                            (112, 'market bgc'),
                                            (112, 'marketmarket'),
                                            (112, 'market market bgc'),
                                            (112, 'market taguig');

-- 113: Serendra / Bonifacio Stopover
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (113, 'serendra'),
                                            (113, 'bonifacio stopover'),
                                            (113, 'serendra bgc'),
                                            (113, 'stopover bgc'),
                                            (113, 'serendra taguig');

-- 114: SM Aura Premier
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (114, 'sm aura'),
                                            (114, 'aura'),
                                            (114, 'aura premier'),
                                            (114, 'sm aura premier'),
                                            (114, 'aura bgc'),
                                            (114, 'aura taguig');

-- 115: Uptown Mall / Uptown Bonifacio
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (115, 'uptown mall'),
                                            (115, 'uptown bonifacio'),
                                            (115, 'uptown bgc'),
                                            (115, 'uptown taguig'),
                                            (115, 'uptown');

-- 116: One Bonifacio High Street
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (116, 'one bgc'),
                                            (116, 'one bonifacio'),
                                            (116, 'one bonifacio high street'),
                                            (116, '1 bgc');

-- 117: Grand Hyatt BGC / Finance Center
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (117, 'grand hyatt'),
                                            (117, 'grand hyatt bgc'),
                                            (117, 'finance center bgc'),
                                            (117, 'finance center'),
                                            (117, 'hyatt bgc');

-- 118: St. Luke's Medical Center BGC
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (118, 'st lukes bgc'),
                                            (118, 'saint lukes bgc'),
                                            (118, 'st lukes'),
                                            (118, 'slmc bgc'),
                                            (118, 'st luke bgc'),
                                            (118, 'saint luke bgc');

-- 119: BGC Bus Terminal
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (119, 'bgc bus terminal'),
                                            (119, 'bgc terminal'),
                                            (119, 'bgc bus stop'),
                                            (119, 'bgc station'),
                                            (119, 'edsa mckinley terminal');

-- 120: McKinley Hill
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (120, 'mckinley'),
                                            (120, 'mckinley hill'),
                                            (120, 'mckinley hill taguig'),
                                            (120, 'mc kinley hill');

-- 121: McKinley West
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (121, 'mckinley west'),
                                            (121, 'mc kinley west'),
                                            (121, 'mckinley west taguig');

-- 122: Venice Grand Canal Mall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (122, 'venice'),
                                            (122, 'grand canal'),
                                            (122, 'venice mckinley'),
                                            (122, 'venice grand canal'),
                                            (122, 'grand canal mall'),
                                            (122, 'venice taguig');

-- 123: The Fort Strip / Enderun
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (123, 'fort strip'),
                                            (123, 'enderun'),
                                            (123, 'the fort'),
                                            (123, 'fort strip bgc'),
                                            (123, 'enderun bgc'),
                                            (123, 'the fort strip');

-- 124: AFP Housing / Gate 3
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (124, 'afp housing'),
                                            (124, 'gate 3 taguig'),
                                            (124, 'gate 3'),
                                            (124, 'afp gate 3'),
                                            (124, 'afp housing taguig');

-- 125: FTI Complex
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (125, 'fti'),
                                            (125, 'fti complex'),
                                            (125, 'fti taguig'),
                                            (125, 'food terminal'),
                                            (125, 'food terminal inc');

-- 126: Bicutan (Market! Market! side)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (126, 'bicutan market'),
                                            (126, 'bicutan taguig'),
                                            (126, 'bicutan mm side'),
                                            (126, 'lower bicutan market');

-- 127: Hagonoy Road / West Service Road Taguig
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (127, 'hagonoy'),
                                            (127, 'wsr taguig'),
                                            (127, 'hagonoy road'),
                                            (127, 'hagonoy taguig'),
                                            (127, 'west service road taguig');

-- 128: Arca South
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (128, 'arca south'),
                                            (128, 'arca'),
                                            (128, 'arca south taguig'),
                                            (128, 'arco south');

-- 129: Kalayaan Flyover / C5
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (129, 'kalayaan'),
                                            (129, 'kalayaan c5'),
                                            (129, 'kalayaan flyover'),
                                            (129, 'c5 kalayaan'),
                                            (129, 'kalayaan ave');

-- 130: Taguig City Hall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (130, 'taguig city hall'),
                                            (130, 'city hall taguig'),
                                            (130, 'taguig hall'),
                                            (130, 'taguig municipal');

-- 131: Lower Bicutan
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (131, 'lower bicutan'),
                                            (131, 'lower bicutan taguig'),
                                            (131, 'lb taguig');

-- 132: Upper Bicutan
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (132, 'upper bicutan'),
                                            (132, 'upper bicutan taguig'),
                                            (132, 'ub taguig');

-- 133: Signal Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (133, 'signal village'),
                                            (133, 'signal taguig'),
                                            (133, 'signal'),
                                            (133, 'signal village taguig');

-- 134: Western Bicutan
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (134, 'western bicutan'),
                                            (134, 'western bicutan taguig'),
                                            (134, 'wb taguig');

-- 135: South Cembo / EMBO Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (135, 'south cembo'),
                                            (135, 'embo'),
                                            (135, 'cembo'),
                                            (135, 'south cembo taguig'),
                                            (135, 'embo taguig'),
                                            (135, 'cembo taguig');

-- 136: Napindan Taguig
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (136, 'napindan taguig'),
                                            (136, 'napindan'),
                                            (136, 'napindan area');

-- 137: Ususan Taguig
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (137, 'ususan'),
                                            (137, 'ususan taguig'),
                                            (137, 'ususan area');

-- ══════════════════════════════════════════════════════════════════════════════
-- MAKATI
-- ══════════════════════════════════════════════════════════════════════════════

-- 138: Ayala MRT Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (138, 'ayala'),
                                            (138, 'ayala mrt'),
                                            (138, 'mrt ayala'),
                                            (138, 'ayala station'),
                                            (138, 'mrt3 ayala'),
                                            (138, 'ayala ave makati');

-- 139: Glorietta
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (139, 'glorietta'),
                                            (139, 'glorietta makati'),
                                            (139, 'gloretta'),
                                            (139, 'glorieta'),
                                            (139, 'glorietta mall');

-- 140: Greenbelt
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (140, 'greenbelt'),
                                            (140, 'greenbelt makati'),
                                            (140, 'green belt'),
                                            (140, 'greenbelt mall'),
                                            (140, 'greenbelt park');

-- 141: One Ayala Terminal
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (141, 'one ayala'),
                                            (141, 'ayala terminal'),
                                            (141, 'one ayala terminal'),
                                            (141, 'one ayala makati'),
                                            (141, '1 ayala');

-- 142: Landmark Makati
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (142, 'landmark'),
                                            (142, 'landmark makati'),
                                            (142, 'the landmark'),
                                            (142, 'landmark mall'),
                                            (142, 'landmark ayala');

-- 143: Rockwell Center
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (143, 'rockwell'),
                                            (143, 'power plant'),
                                            (143, 'power plant mall'),
                                            (143, 'rockwell center'),
                                            (143, 'rockwell makati'),
                                            (143, 'ppm makati');

-- 144: Century City Mall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (144, 'century city'),
                                            (144, 'century city mall'),
                                            (144, 'century city makati'),
                                            (144, 'centuria'),
                                            (144, 'century mall makati');

-- 145: Circuit Makati
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (145, 'circuit'),
                                            (145, 'circuit makati'),
                                            (145, 'circuit lane'),
                                            (145, 'circuit mall'),
                                            (145, 'circuit makati mall');

-- 146: Buendia MRT Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (146, 'buendia mrt'),
                                            (146, 'mrt buendia'),
                                            (146, 'buendia station'),
                                            (146, 'buendia mrt station'),
                                            (146, 'sen gil puyat mrt');

-- 147: Guadalupe MRT Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (147, 'guadalupe'),
                                            (147, 'guadalupe mrt'),
                                            (147, 'mrt guadalupe'),
                                            (147, 'guadalupe station'),
                                            (147, 'guadalupe makati');

-- 148: Magallanes MRT / Interchange
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (148, 'magallanes'),
                                            (148, 'magallanes mrt'),
                                            (148, 'mrt magallanes'),
                                            (148, 'magallanes interchange'),
                                            (148, 'magallanes station'),
                                            (148, 'magallanes makati');

-- 149: Salcedo Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (149, 'salcedo'),
                                            (149, 'salcedo village'),
                                            (149, 'salcedo makati'),
                                            (149, 'salcedo village makati');

-- 150: Legaspi Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (150, 'legaspi'),
                                            (150, 'legaspi village'),
                                            (150, 'legazpi'),
                                            (150, 'legazpi village'),
                                            (150, 'legaspi makati');

-- 151: Bel-Air Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (151, 'bel air'),
                                            (151, 'belair'),
                                            (151, 'bel air makati'),
                                            (151, 'bel air village'),
                                            (151, 'belair village makati');

-- 152: San Lorenzo Village
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (152, 'san lorenzo'),
                                            (152, 'san lorenzo makati'),
                                            (152, 'san lorenzo village makati');

-- 153: Pio del Pilar
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (153, 'pio del pilar'),
                                            (153, 'pio pilar'),
                                            (153, 'pio del pilar makati'),
                                            (153, 'pilar makati');

-- 154: Palanan Makati
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (154, 'palanan'),
                                            (154, 'palanan makati'),
                                            (154, 'palanan area');

-- 155: Olympia / Tejeros Makati
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (155, 'olympia'),
                                            (155, 'tejeros'),
                                            (155, 'olympia makati'),
                                            (155, 'tejeros makati'),
                                            (155, 'olympia tejeros');

-- 156: Dasmarinas Village / Forbes Park
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (156, 'dasmarinas'),
                                            (156, 'forbes park'),
                                            (156, 'dasmari'),
                                            (156, 'dasma village'),
                                            (156, 'dasmarinas village'),
                                            (156, 'forbes makati');

-- 157: Alphaland / Arnaiz Avenue
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (157, 'alphaland'),
                                            (157, 'arnaiz'),
                                            (157, 'paseo de roxas'),
                                            (157, 'alphaland makati'),
                                            (157, 'arnaiz ave'),
                                            (157, 'arnaiz makati');

-- 158: Gil Puyat Avenue Makati
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (158, 'gil puyat makati'),
                                            (158, 'gil puyat ave makati'),
                                            (158, 'sen puyat'),
                                            (158, 'sen gil puyat makati');

-- 159: Buendia / EDSA Makati
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (159, 'edsa makati'),
                                            (159, 'buendia edsa'),
                                            (159, 'edsa buendia makati'),
                                            (159, 'buendia makati edsa');

-- ══════════════════════════════════════════════════════════════════════════════
-- PASAY
-- ══════════════════════════════════════════════════════════════════════════════

-- 160: SM Mall of Asia
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (160, 'moa'),
                                            (160, 'sm moa'),
                                            (160, 'mall of asia'),
                                            (160, 'sm mall of asia'),
                                            (160, 'moa mall'),
                                            (160, 'mall of asia pasay'),
                                            (160, 'sm moa pasay'),
                                            (160, 'moa pasay');

-- 161: MOA Arena / Concert Grounds
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (161, 'moa arena'),
                                            (161, 'arena'),
                                            (161, 'concert grounds'),
                                            (161, 'sm arena'),
                                            (161, 'moa concert'),
                                            (161, 'arena pasay');

-- 162: Entertainment City
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (162, 'entertainment city'),
                                            (162, 'solaire'),
                                            (162, 'okada'),
                                            (162, 'entertain city'),
                                            (162, 'solaire resort'),
                                            (162, 'okada manila'),
                                            (162, 'casino pasay');

-- 163: Ayala Malls Manila Bay (Pasay side)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (163, 'ayala moa'),
                                            (163, 'manila bay pasay'),
                                            (163, 'ayala manila bay pasay'),
                                            (163, 'manila bay mall pasay');

-- 164: EDSA Taft / Pasay Rotonda
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (164, 'edsa taft'),
                                            (164, 'pasay rotonda'),
                                            (164, 'taft pasay'),
                                            (164, 'edsa taft pasay'),
                                            (164, 'rotonda pasay'),
                                            (164, 'taft edsa');

-- 165: LRT-1 Gil Puyat Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (165, 'lrt gil puyat'),
                                            (165, 'lrt buendia'),
                                            (165, 'gil puyat lrt'),
                                            (165, 'buendia lrt'),
                                            (165, 'lrt1 gil puyat');

-- 166: LRT-1 Baclaran Station
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (166, 'baclaran'),
                                            (166, 'lrt baclaran'),
                                            (166, 'baclaran lrt'),
                                            (166, 'lrt1 baclaran'),
                                            (166, 'baclaran station'),
                                            (166, 'baclaran terminal');

-- 167: NAIA Terminal Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (167, 'naia'),
                                            (167, 'airport'),
                                            (167, 'naia terminal'),
                                            (167, 'manila airport'),
                                            (167, 'naia 1'),
                                            (167, 'naia 2'),
                                            (167, 'naia 3'),
                                            (167, 'terminal 3'),
                                            (167, 'terminal 1'),
                                            (167, 'terminal 2');

-- 168: MIA Road / Airport Road
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (168, 'mia road'),
                                            (168, 'airport road'),
                                            (168, 'mia road pasay'),
                                            (168, 'airport road pasay');

-- 169: Villamor Air Base Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (169, 'villamor'),
                                            (169, 'villamor airbase'),
                                            (169, 'villamor air base'),
                                            (169, 'villamor pasay');

-- ══════════════════════════════════════════════════════════════════════════════
-- PATEROS
-- ══════════════════════════════════════════════════════════════════════════════

-- 170: Pateros Town Center / Municipal Hall
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (170, 'pateros'),
                                            (170, 'pateros town center'),
                                            (170, 'pateros municipal'),
                                            (170, 'pateros hall'),
                                            (170, 'pateros center');

-- 171: Pateros Market (Wawa)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (171, 'pateros market'),
                                            (171, 'wawa pateros'),
                                            (171, 'wawa'),
                                            (171, 'pateros wawa'),
                                            (171, 'palengke pateros');

-- 172: Napindan Channel Area (Pateros)
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (172, 'napindan pateros'),
                                            (172, 'napindan channel'),
                                            (172, 'napindan area pateros');

-- 173: Saint Martha Parish Area
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (173, 'saint martha'),
                                            (173, 'sta martha pateros'),
                                            (173, 'santa martha pateros'),
                                            (173, 'st martha pateros'),
                                            (173, 'saint martha church');

-- 174: Pateros / Kapitolyo Boundary
INSERT INTO hub_aliases (hub_id, alias) VALUES
                                            (174, 'kapitolyo boundary'),
                                            (174, 'pateros kapitolyo'),
                                            (174, 'kapitolyo pateros'),
                                            (174, 'pateros pasig boundary');