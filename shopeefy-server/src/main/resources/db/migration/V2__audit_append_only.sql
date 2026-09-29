-- The security event trail is append-only inside the database itself.     [OWASP A09:2025, A08:2025]
-- Even someone holding the application's own DB credentials (e.g. through SQL injection) can
-- insert events but not rewrite or delete them. Changing that needs the TRIGGER privilege,
-- which the application account doesn't have, and any edit made around it breaks the hash chain.
CREATE TRIGGER security_events_no_update BEFORE UPDATE ON security_events
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'security_events is append-only';

CREATE TRIGGER security_events_no_delete BEFORE DELETE ON security_events
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'security_events is append-only';
