-- Translate the demo project descriptions seeded by V2/V3 to English.
-- V2/V3 are left untouched so existing databases keep passing Flyway checksum validation.
UPDATE projects
SET description = 'Pricing and logistics platform'
WHERE id = '11111111-0000-4000-a000-000000000001'
  AND description = 'Pricing- en logistiek platform';

UPDATE projects
SET description = 'HR platform for leave and absence management'
WHERE id = '11111111-0000-4000-a000-000000000002'
  AND description = 'HR platform voor verlof- en verzuimbeheer';
