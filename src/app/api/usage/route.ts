import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";
import { statoQuota } from "@/lib/quota";

export async function GET() {
  const session = await auth();
  if (!session?.user?.email || !session.user.id) {
    return NextResponse.json({ error: "Non autenticato" }, { status: 401 });
  }
  const quota = await statoQuota(session.user.id, session.user.email);
  return NextResponse.json(quota);
}
