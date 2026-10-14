-- Salary breakdown shown to students on the job page (JSON list of {component, amount, note})
ALTER TABLE jobs ADD COLUMN ctc_breakdown TEXT NULL;
