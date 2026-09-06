-- Schema iniziale — Policy Decoder (Scala/ZIO/Quill rewrite).
-- Porta 1:1 il contenuto dei tre modelli Prisma della versione precedente
-- (whitelist + credenziali, log di sicurezza, log delle analisi), con nomi
-- di colonna snake_case per seguire la naming strategy di default di Quill.

CREATE TABLE whitelist_entry (
  id             TEXT PRIMARY KEY,
  email          TEXT NOT NULL UNIQUE,
  password_hash  TEXT,              -- bcrypt; null = login non ancora abilitato
  totp_secret    TEXT,              -- base32, TOTP (RFC 6238); null = 2FA non attiva
  fonte          TEXT NOT NULL,     -- es. "abbonamento", "staff", "beta-tester"
  scadenza       TIMESTAMPTZ,       -- null = nessuna scadenza
  limite_mensile INTEGER NOT NULL DEFAULT 5,  -- -1 = illimitato (admin/staff)
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evento_sicurezza (
  id         TEXT PRIMARY KEY,
  ip         TEXT NOT NULL,
  endpoint   TEXT NOT NULL,  -- es. "/api/auth/login", "/api/analyze", "/api/fetch-url"
  esito      TEXT NOT NULL,  -- "successo" | "fallimento" | "rate-limited"
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_evento_ip_created ON evento_sicurezza (ip, created_at);
CREATE INDEX idx_evento_endpoint_created ON evento_sicurezza (endpoint, created_at);

CREATE TABLE analisi_richiesta (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL REFERENCES whitelist_entry (id) ON DELETE CASCADE,
  modalita          TEXT NOT NULL,  -- "testo" | "url"
  lunghezza_input   INTEGER NOT NULL,
  danger_score      INTEGER,
  esito             TEXT NOT NULL,  -- "successo" | "errore" | "rate-limited" | "quota-esaurita"
  errore_messaggio  TEXT,
  durata_ms         INTEGER,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_analisi_user_created ON analisi_richiesta (user_id, created_at);
