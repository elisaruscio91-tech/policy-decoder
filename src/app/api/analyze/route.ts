import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { ipRichiedente, registraEvento, superaLimite } from "@/lib/sicurezza";
import { chiamaModello, estraiJson } from "@/lib/motore-ia";
import { buildSystemPrompt } from "@/lib/prompt-sistema";
import { statoQuota } from "@/lib/quota";

const ENDPOINT = "/api/analyze";
// Limite anti-DoS per IP, indipendente dalla quota mensile per utente:
// protegge da uno script che martella l'endpoint anche con un solo account.
const LIMITE_TENTATIVI_IP = 30;
const FINESTRA_MINUTI = 10;
const MAX_CARATTERI_INPUT = 20_000;

interface RisultatoAnalisi {
  danger_score?: { score?: number };
}

export async function POST(req: Request) {
  const session = await auth();
  if (!session?.user?.email || !session.user.id) {
    return NextResponse.json({ error: "Accesso richiesto" }, { status: 401 });
  }
  const { id: userId, email } = session.user;

  const ip = await ipRichiedente();
  if (await superaLimite(ip, ENDPOINT, LIMITE_TENTATIVI_IP, FINESTRA_MINUTI)) {
    await registraEvento(ip, ENDPOINT, "rate-limited");
    return NextResponse.json({ error: "Troppe richieste. Riprova tra qualche minuto." }, { status: 429 });
  }

  const body = await req.json().catch(() => null);
  const testo = typeof body?.testo === "string" ? body.testo.trim() : "";
  const modalita = body?.modalita === "url" ? "url" : "testo";
  const outputLang = ["auto", "en", "it"].includes(body?.outputLang) ? body.outputLang : "auto";

  if (!testo) {
    await registraEvento(ip, ENDPOINT, "fallimento");
    return NextResponse.json({ error: "Testo mancante" }, { status: 400 });
  }
  if (testo.length > MAX_CARATTERI_INPUT) {
    await registraEvento(ip, ENDPOINT, "fallimento");
    return NextResponse.json({ error: `Testo troppo lungo (max ${MAX_CARATTERI_INPUT} caratteri)` }, { status: 413 });
  }

  const quota = await statoQuota(userId, email);
  if (quota.rimanenti <= 0) {
    await prisma.analisiRichiesta.create({
      data: { userId, modalita, lunghezzaInput: testo.length, esito: "quota-esaurita" },
    });
    await registraEvento(ip, ENDPOINT, "rate-limited");
    return NextResponse.json(
      { error: "Quota mensile esaurita. Si rinnova all'inizio del prossimo mese.", quota },
      { status: 429 },
    );
  }

  const inizio = Date.now();
  try {
    const raw = await chiamaModello(buildSystemPrompt(outputLang), `Analyze this text:\n\n---\n${testo}\n---`);
    const parsed = estraiJson<RisultatoAnalisi>(raw);
    const durataMs = Date.now() - inizio;

    await prisma.analisiRichiesta.create({
      data: {
        userId,
        modalita,
        lunghezzaInput: testo.length,
        dangerScore: parsed.danger_score?.score ?? null,
        esito: "successo",
        durataMs,
      },
    });
    await registraEvento(ip, ENDPOINT, "successo");

    const quotaAggiornata = await statoQuota(userId, email);
    return NextResponse.json({ risultato: parsed, quota: quotaAggiornata });
  } catch (e) {
    const durataMs = Date.now() - inizio;
    const messaggio = e instanceof Error ? e.message : "Analisi fallita";
    await prisma.analisiRichiesta.create({
      data: {
        userId,
        modalita,
        lunghezzaInput: testo.length,
        esito: "errore",
        erroreMessaggio: messaggio.slice(0, 500),
        durataMs,
      },
    });
    await registraEvento(ip, ENDPOINT, "fallimento");
    return NextResponse.json({ error: messaggio }, { status: 502 });
  }
}
