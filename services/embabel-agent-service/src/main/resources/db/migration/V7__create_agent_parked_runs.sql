-- Runs paused at an approval gate (ADR-001 "parked" runs), so they survive a restart or redeploy.
-- A row exists exactly while the run is parked: parking writes it in the same transaction that sets
-- agent_runs.status to AWAITING_APPROVAL; a decision or Stop deletes it with a
-- run_id + iteration + version check, so only one decision can ever resume the run, also across
-- instances. The startup reconciler leaves runs that have a row alone; the rehydrator re-adopts them.
-- state is the JSON snapshot of the run's PipelineState (PipelineStateCodec, carrying its own
-- formatVersion). It never contains the start request's git token.
CREATE TABLE agent_parked_runs (
    run_id      VARCHAR(64)  NOT NULL PRIMARY KEY REFERENCES agent_runs(run_id) ON DELETE CASCADE,
    gate_stage  VARCHAR(160),
    iteration   INTEGER      NOT NULL,
    state       TEXT         NOT NULL,
    parked_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT       NOT NULL
);
