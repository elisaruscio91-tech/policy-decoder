import type { Metadata } from "next";
import { signIn } from "@/lib/auth";

export const metadata: Metadata = {
  title: "Accedi — Policy Decoder",
  robots: { index: false, follow: false },
};

export default function AccediPage() {
  return (
    <div className="pagina-login">
      <div className="riquadro">
        <h1>Policy Decoder</h1>
        <p>
          Accesso riservato a chi ha un abbonamento attivo. Riceverai un link
          di accesso via email, valido una sola volta — nessuna password da
          ricordare.
        </p>
        <form
          action={async (formData: FormData) => {
            "use server";
            await signIn("resend", formData, { redirectTo: "/" });
          }}
        >
          <label htmlFor="email">La tua email</label>
          <input
            id="email"
            name="email"
            type="email"
            required
            placeholder="nome@esempio.it"
            autoComplete="email"
          />
          <button type="submit">Inviami il link di accesso</button>
        </form>
      </div>
    </div>
  );
}
