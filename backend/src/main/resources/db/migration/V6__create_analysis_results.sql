CREATE TABLE analysis_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id    UUID REFERENCES statements(id) ON DELETE CASCADE,
    analysis_type   VARCHAR(100) CHECK (analysis_type IN (
                        'CATEGORIZATION', 'ANOMALY', 'SUBSCRIPTION', 'SUMMARY'
                    )),
    result_data     JSONB,
    model_used      VARCHAR(100),
    processing_ms   INTEGER,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_analysis_results_statement_id ON analysis_results(statement_id);
CREATE INDEX idx_analysis_results_type ON analysis_results(analysis_type);
CREATE INDEX idx_analysis_results_result_data ON analysis_results USING gin(result_data);
