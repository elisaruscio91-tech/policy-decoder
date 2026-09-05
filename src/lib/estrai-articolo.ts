import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { JSDOM } from "jsdom";
import { Readability } from "@mozilla/readability";

// Estrazione testo articolo lato server: niente chiamate a Claude/OpenRouter
// per il semplice recupero della pagina (risparmio di token e di costo).
// Include protezioni anti-SSRF: solo http/https, niente IP privati/loopback/
// link-local (anche dopo risoluzione DNS e ad ogni redirect seguito a mano).

const MAX_BYTES = 3 * 1024 * 1024; // 3MB
const TIMEOUT_MS = 10_000;
const MAX_REDIRECTS = 3;

class ErroreArticolo extends Error {}

function ipPrivato(ip: string): boolean {
  const v = isIP(ip);
  if (v === 4) {
    const [a, b] = ip.split(".").map(Number);
    return (
      a === 10 ||
      a === 127 ||
      a === 0 ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168)
    );
  }
  if (v === 6) {
    const l = ip.toLowerCase();
    return l === "::1" || l.startsWith("fc") || l.startsWith("fd") || l.startsWith("fe80");
  }
  return true; // non un IP valido: non ci fidiamo
}

async function validaUrlSicuro(url: URL): Promise<void> {
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new ErroreArticolo("Protocollo non consentito");
  }
  const hostname = url.hostname;
  if (hostname === "localhost") throw new ErroreArticolo("Host non consentito");
  if (isIP(hostname) && ipPrivato(hostname)) {
    throw new ErroreArticolo("Indirizzo IP non consentito");
  }
  if (!isIP(hostname)) {
    const risoluzioni = await lookup(hostname, { all: true });
    if (risoluzioni.some((r) => ipPrivato(r.address))) {
      throw new ErroreArticolo("L'host risolve a un indirizzo non consentito");
    }
  }
}

export async function estraiArticolo(urlInput: string): Promise<{ titolo: string; testo: string; fonte: string }> {
  let url: URL;
  try {
    url = new URL(urlInput);
  } catch {
    throw new ErroreArticolo("URL non valido");
  }

  let redirectsRimasti = MAX_REDIRECTS;
  let html = "";

  for (;;) {
    await validaUrlSicuro(url);

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
    let resp: Response;
    try {
      resp = await fetch(url, {
        redirect: "manual",
        signal: controller.signal,
        headers: {
          "User-Agent": "Mozilla/5.0 (compatible; PolicyDecoderBot/1.0; +https://associazionetransgenere.org)",
          Accept: "text/html",
        },
      });
    } finally {
      clearTimeout(timeout);
    }

    if ([301, 302, 303, 307, 308].includes(resp.status)) {
      const location = resp.headers.get("location");
      if (!location || redirectsRimasti <= 0) throw new ErroreArticolo("Troppi redirect o redirect mancante");
      url = new URL(location, url);
      redirectsRimasti -= 1;
      continue;
    }

    if (!resp.ok) throw new ErroreArticolo(`Impossibile recuperare la pagina (${resp.status})`);

    const contentType = resp.headers.get("content-type") || "";
    if (!contentType.includes("text/html") && !contentType.includes("application/xhtml")) {
      throw new ErroreArticolo("Il contenuto non è una pagina HTML");
    }

    const reader = resp.body?.getReader();
    if (!reader) throw new ErroreArticolo("Risposta senza contenuto");
    const chunks: Uint8Array[] = [];
    let totale = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      totale += value.byteLength;
      if (totale > MAX_BYTES) throw new ErroreArticolo("Pagina troppo grande");
      chunks.push(value);
    }
    html = Buffer.concat(chunks.map((c) => Buffer.from(c))).toString("utf-8");
    break;
  }

  const dom = new JSDOM(html, { url: url.toString() });
  const reader = new Readability(dom.window.document);
  const articolo = reader.parse();

  if (!articolo?.textContent?.trim()) {
    throw new ErroreArticolo("Impossibile estrarre il contenuto dell'articolo da questa pagina");
  }

  return {
    titolo: articolo.title || url.hostname,
    testo: articolo.textContent.trim(),
    fonte: url.toString(),
  };
}

export { ErroreArticolo };
