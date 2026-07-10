-- Dossier workflow lifecycle (NIMBA-52): the cross-directorate state machine
-- (DRI → DCM → DRC → Comité), its timeline, and the distinct comité approvals of
-- the current review cycle. References the credit case by id only; no FK crosses
-- the module boundary. Every existing case is backfilled at BROUILLON.

CREATE TABLE case_workflow (
    id             UUID        PRIMARY KEY,
    credit_case_id UUID        NOT NULL,
    status         VARCHAR(32) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_case_workflow_case UNIQUE (credit_case_id)
);

-- Distinct comité members who have approved in the dossier's current cycle; cleared
-- whenever the dossier returns to the DRI. Two entries flip the status to APPROUVE.
CREATE TABLE case_workflow_comite_approver (
    case_workflow_id UUID NOT NULL REFERENCES case_workflow (id) ON DELETE CASCADE,
    approver_id      UUID NOT NULL,
    CONSTRAINT pk_case_workflow_comite_approver PRIMARY KEY (case_workflow_id, approver_id)
);

CREATE TABLE workflow_event (
    id               UUID        PRIMARY KEY,
    credit_case_id   UUID        NOT NULL,
    actor_id         UUID        NOT NULL,
    actor_department VARCHAR(16) NOT NULL,
    action           VARCHAR(32) NOT NULL,
    from_status      VARCHAR(32) NOT NULL,
    to_status        VARCHAR(32) NOT NULL,
    comment          TEXT,
    occurred_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workflow_event_case ON workflow_event (credit_case_id, occurred_at);
CREATE INDEX idx_case_workflow_status ON case_workflow (status);

-- Backfill: every dossier that predates the workflow starts its lifecycle at BROUILLON.
INSERT INTO case_workflow (id, credit_case_id, status, created_at, updated_at)
SELECT gen_random_uuid(), id, 'BROUILLON', now(), now()
FROM credit_case;
