// Aggiunge o rinnova un utente in whitelist (es. ad ogni rinnovo
// dell'abbonamento gestito fuori da questa app — Stripe, bonifico, ecc.).
//
// Uso: npm run invita -- utente@esempio.it [mesi=1] [limiteMensile=5]
import { PrismaClient } from "../src/generated/prisma/client";

const prisma = new PrismaClient();

async function main() {
  const [, , email, mesiArg, limiteArg] = process.argv;
  if (!email) {
    console.error("Uso: npm run invita -- <email> [mesi=1] [limiteMensile=5]");
    process.exit(1);
  }

  const mesi = Number(mesiArg ?? 1);
  const limiteMensile = Number(limiteArg ?? 5);

  const scadenza = new Date();
  scadenza.setMonth(scadenza.getMonth() + mesi);

  const voce = await prisma.whitelistEntry.upsert({
    where: { email: email.toLowerCase() },
    update: { scadenza, limiteMensile, fonte: "abbonamento" },
    create: { email: email.toLowerCase(), fonte: "abbonamento", scadenza, limiteMensile },
  });

  console.log(
    `OK: ${voce.email} attivo fino al ${voce.scadenza?.toISOString().slice(0, 10)} (${voce.limiteMensile} analisi/mese)`,
  );
}

main()
  .catch((e) => { console.error(e); process.exitCode = 1; })
  .finally(() => prisma.$disconnect());
