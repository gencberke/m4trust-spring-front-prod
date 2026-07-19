CREATE TABLE contract_intelligence_extraction_result_version (
    id UUID PRIMARY KEY,
    analysis_job_id UUID NOT NULL UNIQUE REFERENCES contract_intelligence_analysis_job(id),
    schema_version TEXT NOT NULL,
    canonical_result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT contract_intelligence_extraction_result_schema_version_ck CHECK (
        schema_version = '1.0.0'
    ),
    CONSTRAINT contract_intelligence_extraction_result_object_ck CHECK (
        jsonb_typeof(canonical_result) = 'object'
    )
);
