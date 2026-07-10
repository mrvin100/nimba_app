-- In-app notifications (NIMBA-53): fanned out per recipient when the workflow
-- module hands a dossier to the next direction. References the recipient and the
-- credit case by id only; no FK crosses a module boundary.

CREATE TABLE notification (
    id             UUID        PRIMARY KEY,
    recipient_id   UUID        NOT NULL,
    credit_case_id UUID,
    message        TEXT        NOT NULL,
    read           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_recipient ON notification (recipient_id, created_at);
CREATE INDEX idx_notification_recipient_unread ON notification (recipient_id) WHERE read = FALSE;
