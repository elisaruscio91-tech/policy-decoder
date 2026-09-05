import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";
import { ipRichiedente, registraEvento, superaLimite } from "@/lib/sicurezza";
import { estraiArticolo } from "@/lib/estrai-articolo";

const ENDPOINT = "/api/fetch-url";
const LIMITE_TENTATIVI = 20;
const FINESTRA_MINUTI = 10;

export async function POST(req: Request) {
  const session = await auth();
  if (!session?.user?.email) {
    return NextResponse.json({ error: "Accesso richiesto" }, { status: 401 });
  }

  const ip = await ipRichiedente();
  if (await superaLimite(ip, ENDPOINT, LIMITE_TENTATIVI, FINESTRA_MINUTI)) {
    await registraEvento(ip, ENDPOINT, "rate-limited");
    return NextResponse.json({ error: "Troppe richieste. Riprova tra qualche minuto." }, { status: 429 });
  }

  const body = await req.json().catch(() => null);
  const url = typeof body?.url === "string" ? body.url.trim() : "";
  if (!url) {
    await registraEvento(ip, ENDPOINT, "fallimento");
    return NextResponse.json({ error: "URL mancante" }, { status: 400 });
  }

  try {
    const articolo = await estraiArticolo(url);
    await registraEvento(ip, ENDPOINT, "successo");
    return NextResponse.json(articolo);
  } catch (e) {
    await registraEvento(ip, ENDPOINT, "fallimento");
    const messaggio = e instanceof Error ? e.message : "Impossibile recuperare l'articolo";
    return NextResponse.json({ error: messaggio }, { status: 422 });
  }
}
