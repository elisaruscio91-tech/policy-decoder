import { PrismaClient } from "@/generated/prisma/client";

// Singleton per evitare troppe connessioni al DB durante l'hot-reload di
// Next.js in sviluppo (ogni reload altrimenti creerebbe un nuovo client).
const globalForPrisma = globalThis as unknown as {
  prisma: PrismaClient | undefined;
};

export const prisma = globalForPrisma.prisma ?? new PrismaClient();

if (process.env.NODE_ENV !== "production") {
  globalForPrisma.prisma = prisma;
}
