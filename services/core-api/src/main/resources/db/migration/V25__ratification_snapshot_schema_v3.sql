-- Plan 18B-P2: allow ratification snapshot schema version 3 (evidencePolicy).
-- Forward-only; V23 remains frozen.

ALTER TABLE ratification_package_snapshot
    DROP CONSTRAINT IF EXISTS ratification_package_snapshot_schema_version_ck;

ALTER TABLE ratification_package_snapshot
    ADD CONSTRAINT ratification_package_snapshot_schema_version_ck
    CHECK (schema_version IN (1, 2, 3));
