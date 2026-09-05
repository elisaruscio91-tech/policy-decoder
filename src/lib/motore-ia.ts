// Astrazione sul motore di analisi: Anthropic, OpenRouter, o Ollama
// self-hosted (privacy massima: il testo non lascia la tua VM).
// Cambiare motore = cambiare AI_PROVIDER in env, zero modifiche al codice
// che chiama analizza(). Vedi README.md per il confronto tra le opzioni.

const MAX_TOKENS = 8192;

async function chiamaAnthropic(system: string, prompt: string): Promise<string> {
  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) throw new Error("ANTHROPIC_API_KEY non configurata sul server");

  const workspaceId = process.env.ANTHROPIC_WORKSPACE_ID;

  const resp = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": "2023-06-01",
      ...(workspaceId ? { "anthropic-workspace-id": workspaceId } : {}),
    },
    body: JSON.stringify({
      model: process.env.ANTHROPIC_MODEL || "claude-sonnet-4-6",
      max_tokens: MAX_TOKENS,
      system,
      messages: [{ role: "user", content: prompt }],
    }),
  });

  const data = await resp.json();
  if (!resp.ok) throw new Error(data?.error?.message || `Errore Anthropic ${resp.status}`);
  return (data.content ?? []).map((b: { text?: string }) => b.text || "").join("");
}

async function chiamaOpenRouter(system: string, prompt: string): Promise<string> {
  const apiKey = process.env.OPENROUTER_API_KEY;
  if (!apiKey) throw new Error("OPENROUTER_API_KEY non configurata sul server");

  const resp = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model: process.env.OPENROUTER_MODEL || "meta-llama/llama-3.3-70b-instruct",
      max_tokens: MAX_TOKENS,
      messages: [
        { role: "system", content: system },
        { role: "user", content: prompt },
      ],
    }),
  });

  const data = await resp.json();
  if (!resp.ok) throw new Error(data?.error?.message || `Errore OpenRouter ${resp.status}`);
  return data?.choices?.[0]?.message?.content || "";
}

async function chiamaOllama(system: string, prompt: string): Promise<string> {
  const baseUrl = process.env.OLLAMA_BASE_URL;
  if (!baseUrl) throw new Error("OLLAMA_BASE_URL non configurata sul server");

  // Ollama espone un endpoint compatibile OpenAI (/v1/chat/completions).
  // OLLAMA_API_KEY è opzionale: Ollama di per sé non ha autenticazione,
  // va messa se davanti c'è un reverse proxy che la richiede.
  const apiKey = process.env.OLLAMA_API_KEY;

  const resp = await fetch(`${baseUrl.replace(/\/$/, "")}/v1/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(apiKey ? { Authorization: `Bearer ${apiKey}` } : {}),
    },
    body: JSON.stringify({
      model: process.env.OLLAMA_MODEL || "qwen2.5:32b",
      max_tokens: MAX_TOKENS,
      messages: [
        { role: "system", content: system },
        { role: "user", content: prompt },
      ],
    }),
  });

  const data = await resp.json();
  if (!resp.ok) throw new Error(data?.error?.message || `Errore Ollama ${resp.status}`);
  return data?.choices?.[0]?.message?.content || "";
}

export async function chiamaModello(system: string, prompt: string): Promise<string> {
  const provider = (process.env.AI_PROVIDER || "anthropic").toLowerCase();
  if (provider === "openrouter") return chiamaOpenRouter(system, prompt);
  if (provider === "ollama") return chiamaOllama(system, prompt);
  return chiamaAnthropic(system, prompt);
}

// Ripara un JSON troncato (può succedere se la risposta del modello supera
// max_tokens): rimuove l'ultimo valore incompleto e richiude parentesi aperte.
function riparaJsonTroncato(frammento: string): string {
  let riparato = frammento.replace(/,\s*[^{}[\],"]*$/, "");
  let graffe = 0;
  let quadre = 0;
  for (const ch of riparato) {
    if (ch === "{") graffe++;
    else if (ch === "}") graffe--;
    else if (ch === "[") quadre++;
    else if (ch === "]") quadre--;
  }
  while (quadre > 0) { riparato += "]"; quadre--; }
  while (graffe > 0) { riparato += "}"; graffe--; }
  return riparato;
}

export function estraiJson<T = unknown>(raw: string): T {
  const cleaned = raw.replace(/```json\s*/g, "").replace(/```\s*/g, "").trim();
  try {
    return JSON.parse(cleaned) as T;
  } catch {
    const m = cleaned.match(/\{[\s\S]*\}/);
    if (!m) throw new Error("Risposta del modello non interpretabile come JSON");
    try {
      return JSON.parse(m[0]) as T;
    } catch {
      try {
        return JSON.parse(riparaJsonTroncato(m[0])) as T;
      } catch {
        throw new Error("Risposta del modello troncata o non interpretabile. Prova con un testo più corto.");
      }
    }
  }
}
