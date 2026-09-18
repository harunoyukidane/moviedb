-- Controlled country reference data (V2.2-10). `person.place_of_birth` was a
-- free-text VARCHAR(300) with no controlled vocabulary - the same defect
-- `movie.original_language` had before the v2.1 language reference table.
-- "USA", "U.S.A.", "United States" and "Untied States" were all accepted and
-- were four different values to any future grouping or filter.
--
-- Field split (not a replacement): `place_of_birth` stays free text for the
-- city/region part; `birth_country_code` is the new controlled piece. Codes
-- are ISO 3166-1 alpha-2, matching what TMDB's `origin_country`/`iso_3166_1`
-- fields already use, so a future importer change needs no translation table.
--
-- This table lives in the People database (not Catalogue's, unlike
-- language_code) because the column it constrains, person.birth_country_code,
-- is owned by the People service (ADR-1).

CREATE TABLE country_code (
    code VARCHAR(2) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    CHECK (code ~ '^[A-Z]{2}$')
);

INSERT INTO country_code (code, name, display_order) VALUES
  ('AF', 'Afghanistan', 10), ('AL', 'Albania', 20), ('DZ', 'Algeria', 30),
  ('AD', 'Andorra', 40), ('AO', 'Angola', 50), ('AG', 'Antigua and Barbuda', 60),
  ('AR', 'Argentina', 70), ('AM', 'Armenia', 80), ('AU', 'Australia', 90),
  ('AT', 'Austria', 100), ('AZ', 'Azerbaijan', 110), ('BS', 'Bahamas', 120),
  ('BH', 'Bahrain', 130), ('BD', 'Bangladesh', 140), ('BB', 'Barbados', 150),
  ('BY', 'Belarus', 160), ('BE', 'Belgium', 170), ('BZ', 'Belize', 180),
  ('BJ', 'Benin', 190), ('BT', 'Bhutan', 200), ('BO', 'Bolivia', 210),
  ('BA', 'Bosnia and Herzegovina', 220), ('BW', 'Botswana', 230), ('BR', 'Brazil', 240),
  ('BN', 'Brunei', 250), ('BG', 'Bulgaria', 260), ('BF', 'Burkina Faso', 270),
  ('BI', 'Burundi', 280), ('CV', 'Cabo Verde', 290), ('KH', 'Cambodia', 300),
  ('CM', 'Cameroon', 310), ('CA', 'Canada', 320), ('CF', 'Central African Republic', 330),
  ('TD', 'Chad', 340), ('CL', 'Chile', 350), ('CN', 'China', 360),
  ('CO', 'Colombia', 370), ('KM', 'Comoros', 380), ('CG', 'Congo', 390),
  ('CD', 'Congo (DRC)', 400), ('CR', 'Costa Rica', 410), ('CI', 'Cote d''Ivoire', 420),
  ('HR', 'Croatia', 430), ('CU', 'Cuba', 440), ('CY', 'Cyprus', 450),
  ('CZ', 'Czechia', 460), ('DK', 'Denmark', 470), ('DJ', 'Djibouti', 480),
  ('DM', 'Dominica', 490), ('DO', 'Dominican Republic', 500), ('EC', 'Ecuador', 510),
  ('EG', 'Egypt', 520), ('SV', 'El Salvador', 530), ('GQ', 'Equatorial Guinea', 540),
  ('ER', 'Eritrea', 550), ('EE', 'Estonia', 560), ('SZ', 'Eswatini', 570),
  ('ET', 'Ethiopia', 580), ('FJ', 'Fiji', 590), ('FI', 'Finland', 600),
  ('FR', 'France', 610), ('GA', 'Gabon', 620), ('GM', 'Gambia', 630),
  ('GE', 'Georgia', 640), ('DE', 'Germany', 650), ('GH', 'Ghana', 660),
  ('GR', 'Greece', 670), ('GD', 'Grenada', 680), ('GT', 'Guatemala', 690),
  ('GN', 'Guinea', 700), ('GW', 'Guinea-Bissau', 710), ('GY', 'Guyana', 720),
  ('HT', 'Haiti', 730), ('HN', 'Honduras', 740), ('HK', 'Hong Kong', 750),
  ('HU', 'Hungary', 760), ('IS', 'Iceland', 770), ('IN', 'India', 780),
  ('ID', 'Indonesia', 790), ('IR', 'Iran', 800), ('IQ', 'Iraq', 810),
  ('IE', 'Ireland', 820), ('IL', 'Israel', 830), ('IT', 'Italy', 840),
  ('JM', 'Jamaica', 850), ('JP', 'Japan', 860), ('JO', 'Jordan', 870),
  ('KZ', 'Kazakhstan', 880), ('KE', 'Kenya', 890), ('KI', 'Kiribati', 900),
  ('KP', 'North Korea', 910), ('KR', 'South Korea', 920), ('KW', 'Kuwait', 930),
  ('KG', 'Kyrgyzstan', 940), ('LA', 'Laos', 950), ('LV', 'Latvia', 960),
  ('LB', 'Lebanon', 970), ('LS', 'Lesotho', 980), ('LR', 'Liberia', 990),
  ('LY', 'Libya', 1000), ('LI', 'Liechtenstein', 1010), ('LT', 'Lithuania', 1020),
  ('LU', 'Luxembourg', 1030), ('MO', 'Macao', 1040), ('MG', 'Madagascar', 1050),
  ('MW', 'Malawi', 1060), ('MY', 'Malaysia', 1070), ('MV', 'Maldives', 1080),
  ('ML', 'Mali', 1090), ('MT', 'Malta', 1100), ('MH', 'Marshall Islands', 1110),
  ('MR', 'Mauritania', 1120), ('MU', 'Mauritius', 1130), ('MX', 'Mexico', 1140),
  ('FM', 'Micronesia', 1150), ('MD', 'Moldova', 1160), ('MC', 'Monaco', 1170),
  ('MN', 'Mongolia', 1180), ('ME', 'Montenegro', 1190), ('MA', 'Morocco', 1200),
  ('MZ', 'Mozambique', 1210), ('MM', 'Myanmar', 1220), ('NA', 'Namibia', 1230),
  ('NR', 'Nauru', 1240), ('NP', 'Nepal', 1250), ('NL', 'Netherlands', 1260),
  ('NZ', 'New Zealand', 1270), ('NI', 'Nicaragua', 1280), ('NE', 'Niger', 1290),
  ('NG', 'Nigeria', 1300), ('MK', 'North Macedonia', 1310), ('NO', 'Norway', 1320),
  ('OM', 'Oman', 1330), ('PK', 'Pakistan', 1340), ('PW', 'Palau', 1350),
  ('PS', 'Palestine', 1360), ('PA', 'Panama', 1370), ('PG', 'Papua New Guinea', 1380),
  ('PY', 'Paraguay', 1390), ('PE', 'Peru', 1400), ('PH', 'Philippines', 1410),
  ('PL', 'Poland', 1420), ('PT', 'Portugal', 1430), ('PR', 'Puerto Rico', 1440),
  ('QA', 'Qatar', 1450), ('RO', 'Romania', 1460), ('RU', 'Russia', 1470),
  ('RW', 'Rwanda', 1480), ('KN', 'Saint Kitts and Nevis', 1490), ('LC', 'Saint Lucia', 1500),
  ('VC', 'Saint Vincent and the Grenadines', 1510), ('WS', 'Samoa', 1520), ('SM', 'San Marino', 1530),
  ('ST', 'Sao Tome and Principe', 1540), ('SA', 'Saudi Arabia', 1550), ('SN', 'Senegal', 1560),
  ('RS', 'Serbia', 1570), ('SC', 'Seychelles', 1580), ('SL', 'Sierra Leone', 1590),
  ('SG', 'Singapore', 1600), ('SK', 'Slovakia', 1610), ('SI', 'Slovenia', 1620),
  ('SB', 'Solomon Islands', 1630), ('SO', 'Somalia', 1640), ('ZA', 'South Africa', 1650),
  ('SS', 'South Sudan', 1660), ('ES', 'Spain', 1670), ('LK', 'Sri Lanka', 1680),
  ('SD', 'Sudan', 1690), ('SR', 'Suriname', 1700), ('SE', 'Sweden', 1710),
  ('CH', 'Switzerland', 1720), ('SY', 'Syria', 1730), ('TW', 'Taiwan', 1740),
  ('TJ', 'Tajikistan', 1750), ('TZ', 'Tanzania', 1760), ('TH', 'Thailand', 1770),
  ('TL', 'Timor-Leste', 1780), ('TG', 'Togo', 1790), ('TO', 'Tonga', 1800),
  ('TT', 'Trinidad and Tobago', 1810), ('TN', 'Tunisia', 1820), ('TR', 'Turkey', 1830),
  ('TM', 'Turkmenistan', 1840), ('TV', 'Tuvalu', 1850), ('UG', 'Uganda', 1860),
  ('UA', 'Ukraine', 1870), ('AE', 'United Arab Emirates', 1880), ('GB', 'United Kingdom', 1890),
  ('US', 'United States', 1900), ('UY', 'Uruguay', 1910), ('UZ', 'Uzbekistan', 1920),
  ('VU', 'Vanuatu', 1930), ('VA', 'Vatican City', 1940), ('VE', 'Venezuela', 1950),
  ('VN', 'Vietnam', 1960), ('YE', 'Yemen', 1970), ('ZM', 'Zambia', 1980),
  ('ZW', 'Zimbabwe', 1990);

-- The importer writes placeOfBirth: null for every imported person, so seeded
-- data has nothing to parse - this migration only has to handle hand-entered
-- values, and (matching V5__add_language_reference_data.sql) nulls out
-- anything that doesn't map, as a safety net rather than an expected path.
ALTER TABLE person ADD COLUMN birth_country_code VARCHAR(2);

UPDATE person SET birth_country_code = NULL
WHERE birth_country_code IS NOT NULL
  AND birth_country_code NOT IN (SELECT code FROM country_code);

ALTER TABLE person
  ADD CONSTRAINT fk_person_birth_country FOREIGN KEY (birth_country_code) REFERENCES country_code(code);
