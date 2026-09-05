import NextAuth from "next-auth";
import type { NextAuthConfig } from "next-auth";
import Resend from "next-auth/providers/resend";
import { PrismaAdapter } from "@auth/prisma-adapter";
import { prisma } from "@/lib/prisma";
import { ipRichiedente, registraEvento, superaLimite } from "@/lib/sicurezza";

const ENDPOINT = "/api/auth/signin";
const LIMITE_TENTATIVI = 5;
const FINESTRA_MINUTI = 10;

const config: NextAuthConfig = {
  adapter: PrismaAdapter(prisma),
  providers: [
    Resend({
      apiKey: process.env.RESEND_API_KEY,
      from: process.env.AUTH_EMAIL_FROM,
    }),
  ],
  pages: {
    signIn: "/accedi",
  },
  callbacks: {
    // Login concesso solo a chi ha una voce whitelist attiva (non scaduta).
    // Ogni tentativo, riuscito o no, viene loggato per rilevare
    // brute-force/DoS sull'endpoint di autenticazione.
    async signIn({ user }) {
      const ip = await ipRichiedente();

      if (await superaLimite(ip, ENDPOINT, LIMITE_TENTATIVI, FINESTRA_MINUTI)) {
        await registraEvento(ip, ENDPOINT, "rate-limited");
        return false;
      }

      const email = user.email?.toLowerCase();
      if (!email) {
        await registraEvento(ip, ENDPOINT, "fallimento");
        return false;
      }

      const voce = await prisma.whitelistEntry.findUnique({ where: { email } });
      const attiva = Boolean(voce) && (!voce!.scadenza || voce!.scadenza > new Date());

      await registraEvento(ip, ENDPOINT, attiva ? "successo" : "fallimento");

      return attiva;
    },
    async session({ session, user }) {
      if (session.user) session.user.id = user.id;
      return session;
    },
  },
};

export const { handlers, auth, signIn, signOut } = NextAuth(config);
