import { redirect } from "next/navigation";
import { auth, signOut } from "@/lib/auth";
import { statoQuota } from "@/lib/quota";
import { PolicyDecoder } from "@/components/PolicyDecoder";

export default async function Home() {
  const session = await auth();
  if (!session?.user?.email || !session.user.id) redirect("/accedi");

  const quota = await statoQuota(session.user.id, session.user.email);

  return (
    <>
      <div className="barra-utente">
        <span>{session.user.email}</span>
        <form
          action={async () => {
            "use server";
            await signOut({ redirectTo: "/accedi" });
          }}
        >
          <button type="submit">Esci</button>
        </form>
      </div>
      <PolicyDecoder quotaIniziale={quota} />
    </>
  );
}
