import { headers } from "next/headers";
import { prisma } from "@/lib/prisma";

// Helper condivisi per rilevare brute-force/DoS su login e API. Stesso
// pattern usato in res-publica: log su Postgres (EventoSicurezza), niente
// servizi esterni aggiuntivi (Redis/Upstash) da pagare o mantenere.

export async function ipRichiedente(): Promise<string> {
  const h = await headers();
  const forwarded = h.get("x-forwarded-for");
  return forwarded?.split(",")[0]?.trim() || "sconosciuto";
}

export async function registraEvento(ip: string, endpoint: string, esito: string) {
  await prisma.eventoSicurezza.create({ data: { ip, endpoint, esito } });
}

/**
 * Ritorna true se l'IP ha superato il numero massimo di tentativi
 * sull'endpoint nella finestra temporale indicata (in minuti).
 * Non registra l'evento: chi chiama decide come/se loggarlo.
 */
export async function superaLimite(
  ip: string,
  endpoint: string,
  limite: number,
  finestraMinuti: number,
): Promise<boolean> {
  const finestraInizio = new Date(Date.now() - finestraMinuti * 60 * 1000);
  const tentativiRecenti = await prisma.eventoSicurezza.count({
    where: { ip, endpoint, createdAt: { gte: finestraInizio } },
  });
  return tentativiRecenti >= limite;
}
