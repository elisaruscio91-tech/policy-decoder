# 🛡️ Policy Decoder

**Trans Rights Media Literacy & Counter-Propaganda Tool**

Decode anti-trans propaganda with science and evidence. Paste any text — or a URL — and get a rigorous, evidence-based analysis.

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)

---

## ✨ What It Does

Policy Decoder analyzes any text for anti-trans propaganda using:

- **6 academic frameworks**: Ellul, Chomsky & Herman, Cialdini, Arendt, Fairclough, and Critical Discourse Analysis
- **Peer-reviewed science**: WPATH SOC-8, Endocrine Society, CCES Scientific Review, Harper et al., Roberts et al.
- **Legal frameworks**: Yogyakarta Principles, ECHR, EU Charter of Fundamental Rights, Italian constitutional law, CAS precedents

### Output Structure

| Section | What it does |
|---------|-------------|
| 📋 **Summary** | What the text says vs. its real intent |
| 🚨 **Danger Score** | 0-100 score with animated ring visualization |
| 🔍 **Rhetorical Techniques** | Framework-by-framework breakdown with severity bars |
| 🔬 **Fact-Check** | Claim → Evidence → Verdict (True/False/Misleading/Unverifiable) |
| ⚖️ **Legal Context** | Relevant norms, rulings, and human rights principles |
| 💪 **Counter-Narrative** | Evidence-based talking points with sources |
| 📱 **Story Slides** | 5 ready-to-share social media slides |

### Features

- 🌐 **Bilingual interface** (English / Italian)
- 🔗 **Paste text or analyze a URL** (server-side article extraction, no extra AI cost)
- 🔒 **Invite-only login** (email magic link, no passwords) tied to an active subscription
- 📊 **Monthly usage quota per user** (default 5 analyses/month) to keep AI costs predictable
- 🛡️ **Anti brute-force/DoS protections** on login and API endpoints
- 📈 **Usage analytics** (who used it, how often, how it performed) without storing the analyzed text
- 🎨 **Dark editorial design** with Playfair Display + DM Sans typography

---

## 🏗️ Architecture

Next.js 16 (App Router) on Vercel, Postgres (Neon) via Prisma, NextAuth v5 with the Resend email provider. Same security pattern as our `res-publica` project: a `WhitelistEntry` table gates login, an `EventoSicurezza` table logs every login/API attempt by IP to detect abuse.

```
src/
  app/
    page.tsx                 → home (protected, server component: checks session + quota)
    accedi/page.tsx           → login (magic link)
    api/auth/[...nextauth]/   → NextAuth handlers
    api/analyze/route.ts      → server-side proxy to the AI provider, enforces quota + rate limit
    api/fetch-url/route.ts    → server-side article extraction (Readability), SSRF-guarded
    api/usage/route.ts        → current user's quota status
  components/PolicyDecoder.tsx → the UI (client component)
  lib/
    auth.ts, prisma.ts, sicurezza.ts, quota.ts, motore-ia.ts, estrai-articolo.ts, prompt-sistema.ts
  middleware.ts               → requires login on all pages except /accedi and /api
prisma/schema.prisma
scripts/invita-utente.ts      → add/renew a user's access (run on subscription renewal)
```

---

## 🚀 Setup

### Prerequisites

- Node.js 20+
- A Postgres database (we use [Neon](https://neon.tech), same as res-publica — can be a second database in the same Neon project)
- A [Resend](https://resend.com) account + verified sending domain (for magic-link emails)
- An Anthropic API key **or** an OpenRouter API key (see "Sostenibilità dei costi" below)

### Local development

```bash
npm install
cp .env.example .env.local   # fill in DATABASE_URL, AUTH_SECRET, RESEND_API_KEY, AUTH_EMAIL_FROM, ANTHROPIC_API_KEY
npx auth secret               # generates AUTH_SECRET if you don't have one
npx prisma migrate dev        # creates tables
npm run dev
```

Add yourself to the whitelist so you can log in:

```bash
npm run invita -- tua-email@esempio.it 12   # 12 mesi di validità, 5 analisi/mese di default
```

### Deploy to Vercel

1. Push to GitHub (this repo).
2. Import the project in Vercel.
3. Set the environment variables from `.env.example` in the Vercel project settings (Production **and** Preview).
4. Vercel auto-detects Next.js. First deploy will fail on `prisma generate` if `DATABASE_URL` isn't set — add it before deploying.
5. Run `npx prisma migrate deploy` once against the production `DATABASE_URL` (locally, pointing at the prod DB, or via a Vercel deploy hook).

**Never** set `ANTHROPIC_API_KEY` / `OPENROUTER_API_KEY` as `NEXT_PUBLIC_*` — they must stay server-only.

---

## 💰 Sostenibilità dei costi (motore di analisi)

Il costo variabile di questo servizio è quasi interamente il consumo di token del modello AI. Le protezioni già in posto:

1. **Quota mensile per utente** (`WhitelistEntry.limiteMensile`, default 5): il costo massimo per utente/mese è quindi noto e limitato a monte, indipendentemente da quanto lo usa.
2. **Prompt tarato per essere conciso** (`prompt-sistema.ts`): output limitato a ~7000 token, niente paragrafi ridondanti.
3. **Estrazione articoli lato server** senza chiamare il modello (Readability), invece di usare una chiamata AI con `web_search` solo per recuperare il testo — dimezza il costo per ogni analisi da URL.

### Scelta del motore (`AI_PROVIDER`)

| Opzione | Costo | Qualità | Infrastruttura richiesta |
|---|---|---|---|
| **Anthropic (default)** | pay-per-token, il più caro dei tre | Il prompt è tarato su Claude, qualità migliore e più affidabile sul JSON strutturato | Nessuna, solo la chiave API |
| **OpenRouter** (`AI_PROVIDER=openrouter`) | pay-per-token, modelli aperti spesso 5-10× più economici (es. Llama 3.3 70B) | Buona ma va validata sul formato JSON e sulla qualità delle citazioni — consigliato testare prima di spegnere Anthropic | Nessuna, un solo endpoint compatibile OpenAI |
| **Ollama self-hosted** | fisso (costo del server), zero per token | Dipende dal modello scelto e dall'hardware; richiede tuning del prompt | **Una VM sempre accesa** con abbastanza RAM/GPU — **Vercel non può ospitarlo** (le funzioni serverless sono stateless e a tempo limitato, non un processo persistente). Servirebbe un piccolo VPS (Fly.io, Hetzner, un mini-PC in casa) raggiungibile dalle API route di Vercel via HTTP interno |

**Raccomandazione**: restare su Anthropic con la quota mensile stretta finché il numero di abbonati è piccolo (il costo per utente è comunque limitato dal cap). Quando i volumi crescono, provare OpenRouter con un modello aperto come opzione B testandone la qualità in parallelo (basta cambiare `AI_PROVIDER` in staging). Investire in una VM per Ollama solo se il volume mensile rende conveniente un costo fisso rispetto al pay-per-token — sotto qualche centinaio di analisi/mese difficilmente conviene rispetto a OpenRouter.

### Ciclo di abbonamento

La whitelist non è collegata (ancora) a un sistema di pagamento automatico. Ad ogni rinnovo dell'abbonamento (gestito altrove, es. Stripe/bonifico):

```bash
npm run invita -- utente@esempio.it 1   # rinnova per 1 mese
```

Se l'abbonamento scade e non viene rinnovato, `scadenza` passa e il login viene rifiutato automaticamente al prossimo tentativo — nessuna azione manuale di revoca necessaria.

---

## 🔒 Sicurezza

- Login ristretto a email in whitelist attiva (magic link, nessuna password memorizzata).
- Ogni tentativo di login e ogni chiamata a `/api/analyze` / `/api/fetch-url` è loggata per IP (`EventoSicurezza`) con rate limiting: oltre soglia, l'IP viene bloccato temporaneamente (protezione anti brute-force e DoS di base, senza servizi esterni a pagamento).
- `/api/fetch-url` valida l'URL (solo http/https, blocco IP privati/loopback anche dopo risoluzione DNS e ad ogni redirect) per prevenire SSRF.
- CSP stretta (`next.config.ts`): nessuna chiamata esterna dal browser, tutto passa dalle API route del server.
- Le chiavi AI non sono mai esposte lato client.

---

## 🔬 Scientific Sources

| Source | What it shows |
|--------|-------------|
| **CCES Scientific Review (2022)** | No firm basis for performance advantage after 12mo testosterone suppression |
| **Harper et al. (2021, BJSM)** | Systematic review on HRT effects on body composition |
| **Harper (2015)** | Age-graded performance equivalence in trans women runners |
| **Roberts et al. (2021)** | Military fitness data showing significant reductions post-HRT |
| **Jones et al. (2016, Sports Medicine)** | Systematic review: athletic advantage is overinterpreted |
| **WPATH SOC-8 (2022)** | Standards of Care for transgender health |
| **Karkazis et al.** | Testosterone not predictive of athletic performance |

## 📚 Propaganda Analysis Frameworks

| Framework | Focus |
|-----------|-------|
| **Ellul** | Integral propaganda: agitation vs integration, vertical vs horizontal |
| **Chomsky & Herman** | Manufacturing Consent: 5 filters (ownership, advertising, sourcing, flak, ideology) |
| **Cialdini** | Weapons of influence: reciprocity, commitment, social proof, authority, liking, scarcity |
| **Arendt** | Banality of evil: normalization, us-vs-them, obedience, critical thinking erosion |
| **Fairclough** | Critical Discourse Analysis: power relations, ideology in language, nominalization |

---

## ⚖️ License

MIT — Use it, fork it, share it. Built for the community.

---

## 💜 Solidarity

This tool exists because trans rights are human rights. It is built for advocates, journalists, educators, and anyone who needs evidence-based tools to counter misinformation.

> *"The tool's strength is precision, not overstatement."*

Trans people have always existed. Trans people will always exist. This tool helps ensure the facts are heard.

🏳️‍⚧️
