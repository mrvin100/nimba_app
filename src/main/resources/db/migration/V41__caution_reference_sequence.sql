-- Reference numbers are no longer drawn from one global counter shared by
-- every document type and the dossier. Each series (SMS, ACF, AFC, DOS) now
-- increments on its own, and the 5-digit sequence becomes a value the DCM
-- analyst enters (pre-filled with a suggestion, still free to change) rather
-- than one the platform assigns silently. A PRO carries no series of its own
-- at all: it is the same Caution de Soumission with revised dates, so it
-- copies its origin SMS's reference_number and sequence verbatim.

ALTER TABLE caution ADD COLUMN sequence INT;
UPDATE caution SET sequence = split_part(reference_number, '-', 1)::int;
ALTER TABLE caution ALTER COLUMN sequence SET NOT NULL;

ALTER TABLE caution DROP CONSTRAINT uq_caution_reference_number;
-- A PRO deliberately duplicates its origin SMS's reference_number and
-- sequence (possibly shared with other PROs on the same SMS across repeated
-- prorogations), so it is excluded from the uniqueness check entirely; SMS,
-- ACF and AFC each still enforce one sequence per series.
CREATE UNIQUE INDEX uq_caution_type_sequence ON caution (document_type, sequence) WHERE document_type <> 'PRO';

ALTER TABLE caution_dossier ADD COLUMN sequence INT;
UPDATE caution_dossier SET sequence = reverse(split_part(reverse(reference_number), '-', 1))::int;
ALTER TABLE caution_dossier ALTER COLUMN sequence SET NOT NULL;
ALTER TABLE caution_dossier ADD CONSTRAINT uq_caution_dossier_sequence UNIQUE (sequence);

-- The suggested-next-value and duplicate check now read straight from the
-- (document_type, sequence) / (sequence) data above, so the counter that
-- assigned numbers automatically has nothing left to back.
DROP TABLE caution_counter;
