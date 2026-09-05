import { prisma } from "@/lib/prisma";

function inizioMeseSolareCorrente(): Date {
  const d = new Date();
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  return d;
}

export async function limiteMensilePer(email: string): Promise<number> {
  const voce = await prisma.whitelistEntry.findUnique({ where: { email: email.toLowerCase() } });
  return voce?.limiteMensile ?? Number(process.env.DEFAULT_MONTHLY_QUOTA || 5);
}

export async function analisiUsateQuestoMese(userId: string): Promise<number> {
  return prisma.analisiRichiesta.count({
    where: { userId, esito: "successo", createdAt: { gte: inizioMeseSolareCorrente() } },
  });
}

export async function statoQuota(userId: string, email: string) {
  const [limite, usate] = await Promise.all([limiteMensilePer(email), analisiUsateQuestoMese(userId)]);
  return { limite, usate, rimanenti: Math.max(0, limite - usate) };
}
