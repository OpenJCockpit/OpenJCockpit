-- workflow-execution-state-to-database: the sanitised, bounded terminal failure summary of a
-- FAILED run (exception class chain + an allow-listed reason; never a raw exception message,
-- path, repository URL or credential). Null for every run that has not failed, and for every
-- pre-existing V1 row. See RunFailureDiagnostics.MAX_LENGTH — the 512 below must equal it.
ALTER TABLE agent_runs ADD COLUMN failure_summary VARCHAR(512);
