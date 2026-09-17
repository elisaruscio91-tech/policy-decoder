-- Archivio fonti (RAG): documenti caricati manualmente via CLI
-- (decoder.backend.cli.IngestaFonte), indicizzati come chunk con embedding,
-- usati da Fonti.contestoPer per iniettare contesto pertinente nel prompt di
-- analisi. Vedi README.md § "Archivio fonti (RAG)".
--
-- ATTENZIONE: `vector(768)` è dimensionato per il modello di embedding di
-- default (nomic-embed-text via Ollama/OmniRoute, EMBEDDING_MODEL). Se si
-- sceglie un modello con dimensione diversa, questa colonna va adattata
-- PRIMA del primo avvio (Flyway non permette di rieseguire una migration
-- già applicata).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE fonte_documento (
  id UUID PRIMARY KEY,
  titolo TEXT NOT NULL,
  origine TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fonte_chunk (
  id UUID PRIMARY KEY,
  documento_id UUID NOT NULL REFERENCES fonte_documento(id) ON DELETE CASCADE,
  ordine INT NOT NULL,
  testo TEXT NOT NULL,
  embedding vector(768) NOT NULL
);

-- HNSW: indice approssimato per la ricerca di similarità (cosine distance,
-- operatore `<->` con vector_cosine_ops usato in FontiRepo.cercaSimili).
CREATE INDEX fonte_chunk_embedding_idx ON fonte_chunk USING hnsw (embedding vector_cosine_ops);
