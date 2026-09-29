-- Richieste di accesso self-service (form "Registrati" nel frontend):
-- l'utente sceglie email+password ma non entra subito — resta "in_attesa"
-- finché l'admin approva a mano (vedi decoder.backend.cli.GestisciRichieste),
-- momento in cui la password già hashata qui viene copiata in
-- whitelist_entry. Non c'è un vincolo UNIQUE su email: una vecchia richiesta
-- rifiutata non deve impedirne una nuova, il controllo "già in_attesa" è
-- applicativo (vedi Repo.trovaRichiestaPendente).
CREATE TABLE richiesta_accesso (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  stato         TEXT NOT NULL DEFAULT 'in_attesa',  -- 'in_attesa' | 'approvata' | 'rifiutata'
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  gestita_at    TIMESTAMPTZ
);
CREATE INDEX idx_richiesta_stato ON richiesta_accesso (stato, created_at);
