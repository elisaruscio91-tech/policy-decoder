"use client";

import { useState, useRef, useCallback, useEffect } from "react";

// ═══════════════════════════════════════════════════════════════════
// ACET Policy Decoder — Trans Rights Media Literacy Tool
// © 2026 ACET — Associazione per la Cultura e l'Etica Transgenere
// https://www.associazionetransgenere.org
//
// Versione Next.js: l'analisi passa da /api/analyze (chiave del modello
// tenuta lato server, quota mensile e rate limiting applicati lì).
// ═══════════════════════════════════════════════════════════════════

type Quota = { limite: number; usate: number; rimanenti: number };

// ─── i18n ────────────────────────────────────────────────────────
const UI = {
  en: {
    appName: "Policy Decoder",
    appOrg: "ACET",
    appOrgFull: "Associazione per la Cultura e l'Etica Transgenere",
    appSub: "Trans Rights Media Literacy Tool",
    heroTitle: "Decode anti-trans propaganda with science & evidence",
    heroDesc: "Paste any text — a policy document, political statement, news article, social media post — and get a rigorous, evidence-based analysis using peer-reviewed science, legal frameworks, and propaganda studies.",
    placeholder: "Paste text here: political statements, policy documents, news articles, social media posts, talk show transcripts...",
    analyzeBtn: "Analyze",
    analyzing: "Analyzing…",
    outputLangLabel: "Output language",
    outputLangAuto: "Same as input",
    clearBtn: "Clear & New Analysis",
    sections: {
      summary: "Summary", danger: "Danger Score", rhetoric: "Rhetorical Techniques",
      factcheck: "Fact-Check", legal: "Legal Context", counter: "Counter-Narrative", slides: "Story Slides",
    },
    sectionIcons: { summary: "📋", danger: "🚨", rhetoric: "🔍", factcheck: "🔬", legal: "⚖️", counter: "💪", slides: "📱" },
    errorGeneric: "Analysis failed. Please try again.",
    errorEmpty: "Please paste some text to analyze.",
    errorEmptyUrl: "Please enter a URL to analyze.",
    errorInvalidUrl: "Please enter a valid URL (starting with http:// or https://).",
    errorQuotaEsaurita: "Monthly quota reached. It resets at the start of next month.",
    errorUrlFetch: "Could not retrieve content from this URL. Try pasting the text directly.",
    tabText: "Paste text",
    tabUrl: "Analyze URL",
    urlPlaceholder: "https://example.com/article-to-analyze",
    urlFetching: "Fetching article…",
    urlSource: "Source",
    poweredBy: "Powered by evidence. Built for advocacy, not propaganda.",
    disclaimer: "Educational & media literacy tool. Not legal advice. All scientific claims reference peer-reviewed sources.",
    exampleLabel: "Try an example",
    examples: [
      { label: "🏛️ Political statement", text: "Transgender athletes have an unfair biological advantage. We must protect women's sports by banning all trans women from competing in female categories. The science is clear: biological males will always be stronger, faster, and more powerful than biological females, regardless of hormone therapy. We need to protect our daughters and ensure fair competition for real women." },
      { label: "📰 News framing", text: "Concerns grow over fairness in women's sport as more transgender athletes seek to compete. Critics say biological differences give trans women an insurmountable advantage, while supporters argue current hormone requirements already level the playing field. The debate continues to divide opinion among athletes, scientists and policy makers alike." },
      { label: "📱 Social post", text: "Men don't belong in women's sports. Period. No amount of hormones changes your DNA. XY is XY. Protect our daughters from this insanity. #SaveWomensSports #BiologyMatters #ProtectTitle9" },
    ],
    analyzedText: "Analyzed text",
    sevLabel: "Severity",
    declaredIntent: "DECLARED INTENT",
    actualIntent: "ACTUAL INTENT",
    quotaLabel: "analyses left this month",
    privacyLink: "Privacy & Data",
    privacyModal: {
      title: "Privacy & Data Processing",
      close: "Close",
      sections: [
        { heading: "What this tool does", text: "Policy Decoder analyzes text for propaganda techniques, factual accuracy, and rhetorical strategies. Developed by ACET." },
        { heading: "How your data is processed", text: "The text you submit is sent, from our server, to the configured AI provider for analysis. It is not stored beyond the usage log needed to enforce your monthly quota (input length, danger score, timestamp — never the text itself)." },
        { heading: "Access", text: "This tool requires login (email magic link) restricted to active subscribers. Login attempts and API usage are logged by IP for a limited time solely to detect abuse (brute-force, denial of service)." },
        { heading: "Contact", text: "info@associazionetransgenere.org" },
      ],
    },
  },
  it: {
    appName: "Policy Decoder",
    appOrg: "ACET",
    appOrgFull: "Associazione per la Cultura e l'Etica Transgenere",
    appSub: "Media Literacy per i Diritti Trans",
    heroTitle: "Decodifica la propaganda anti-trans con scienza ed evidenze",
    heroDesc: "Incolla qualsiasi testo — un documento di policy, una dichiarazione politica, un articolo di giornale, un post social — e ottieni un'analisi rigorosa basata su scienza peer-reviewed, framework legali e studi sulla propaganda.",
    placeholder: "Incolla il testo qui: dichiarazioni politiche, documenti di policy, articoli di giornale, post social, trascrizioni di talk show...",
    analyzeBtn: "Analizza",
    analyzing: "Analisi in corso…",
    outputLangLabel: "Lingua output",
    outputLangAuto: "Come l'input",
    clearBtn: "Pulisci & Nuova analisi",
    sections: {
      summary: "Sommario", danger: "Danger Score", rhetoric: "Tecniche Retoriche",
      factcheck: "Fact-Check", legal: "Contesto Legale", counter: "Counter-Narrative", slides: "Story Slides",
    },
    sectionIcons: { summary: "📋", danger: "🚨", rhetoric: "🔍", factcheck: "🔬", legal: "⚖️", counter: "💪", slides: "📱" },
    errorGeneric: "Analisi fallita. Riprova.",
    errorEmpty: "Incolla del testo da analizzare.",
    errorEmptyUrl: "Inserisci un URL da analizzare.",
    errorInvalidUrl: "Inserisci un URL valido (che inizi con http:// o https://).",
    errorQuotaEsaurita: "Quota mensile esaurita. Si rinnova all'inizio del prossimo mese.",
    errorUrlFetch: "Impossibile recuperare il contenuto da questo URL. Prova a incollare il testo direttamente.",
    tabText: "Incolla testo",
    tabUrl: "Analizza URL",
    urlPlaceholder: "https://esempio.com/articolo-da-analizzare",
    urlFetching: "Recupero dell'articolo…",
    urlSource: "Fonte",
    poweredBy: "Alimentato dalle evidenze. Costruito per l'advocacy, non per la propaganda.",
    disclaimer: "Strumento educativo e di media literacy. Non costituisce consulenza legale. Le citazioni scientifiche fanno riferimento a fonti peer-reviewed.",
    exampleLabel: "Prova un esempio",
    examples: [
      { label: "🏛️ Dichiarazione politica", text: "Le atlete transgender hanno un vantaggio biologico ingiusto. Dobbiamo proteggere lo sport femminile vietando a tutte le donne trans di competere nelle categorie femminili. La scienza è chiara: i maschi biologici saranno sempre più forti, veloci e potenti delle femmine biologiche, indipendentemente dalla terapia ormonale. Dobbiamo proteggere le nostre figlie." },
      { label: "📰 Framing giornalistico", text: "Crescono le preoccupazioni sulla correttezza nello sport femminile con l'aumento delle atlete transgender. I critici sostengono che le differenze biologiche diano alle donne trans un vantaggio insormontabile, mentre i sostenitori argomentano che i requisiti ormonali attuali già livellano il campo di gioco. Il dibattito continua a dividere l'opinione pubblica." },
      { label: "📱 Post social", text: "Gli uomini non appartengono agli sport femminili. Punto. Nessuna quantità di ormoni cambia il tuo DNA. XY è XY. Proteggiamo le nostre figlie da questa follia. #SalviamoLoSportFemminile #LaBiologiaConta" },
    ],
    analyzedText: "Testo analizzato",
    sevLabel: "Severità",
    declaredIntent: "INTENTO DICHIARATO",
    actualIntent: "INTENTO REALE",
    quotaLabel: "analisi rimanenti questo mese",
    privacyLink: "Privacy e Dati",
    privacyModal: {
      title: "Privacy e Trattamento Dati",
      close: "Chiudi",
      sections: [
        { heading: "Cosa fa questo strumento", text: "Policy Decoder analizza testi per identificare tecniche di propaganda, accuratezza fattuale e strategie retoriche. Sviluppato da ACET." },
        { heading: "Come vengono trattati i tuoi dati", text: "Il testo che invii viene inviato, dal nostro server, al provider AI configurato per l'analisi. Non viene conservato oltre il log d'uso necessario ad applicare la quota mensile (lunghezza input, danger score, data/ora — mai il testo stesso)." },
        { heading: "Accesso", text: "Lo strumento richiede login (magic link via email) riservato agli abbonati attivi. I tentativi di accesso e l'uso delle API vengono loggati per IP per un tempo limitato, solo per rilevare abusi (brute-force, denial of service)." },
        { heading: "Contatti", text: "info@associazionetransgenere.org" },
      ],
    },
  },
} as const;

type Lingua = keyof typeof UI;
type UITesti = (typeof UI)[Lingua];

// ─── HELPERS ─────────────────────────────────────────────────────
const dangerColor = (s: number) => s >= 80 ? "#dc2626" : s >= 60 ? "#ea580c" : s >= 40 ? "#d97706" : s >= 20 ? "#2563eb" : "#16a34a";

const verdictColor = (v?: string) => {
  if (!v) return "#64748b";
  const vl = v.toLowerCase();
  if (vl.includes("false") || vl.includes("falso")) return "#dc2626";
  if (vl.includes("misleading") || vl.includes("fuorviante")) return "#ea580c";
  if (vl.includes("partially") || vl.includes("parzialmente")) return "#d97706";
  if (vl.includes("unverifiable") || vl.includes("non verificabile")) return "#9333ea";
  if (vl === "true" || vl === "vero") return "#16a34a";
  return "#64748b";
};

// ─── SUB-COMPONENTS ──────────────────────────────────────────────
function SectionHead({ icon, title }: { icon: string; title: string }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 18, marginTop: 40 }}>
      <span style={{ fontSize: 22 }}>{icon}</span>
      <h2 style={{ fontFamily: "var(--font-playfair), Georgia, serif", fontSize: 22, fontWeight: 800, margin: 0, color: "#f1f5f9" }}>{title}</h2>
    </div>
  );
}

function Card({ children, accent }: { children: React.ReactNode; accent?: string }) {
  return (
    <div style={{
      background: "#0b0b1c", border: "1px solid #181836",
      borderLeft: accent ? `3px solid ${accent}` : undefined,
      borderRadius: 12, padding: "20px 22px", marginBottom: 12,
    }}>{children}</div>
  );
}

function DangerRing({ score, label }: { score: number; label: string }) {
  const [val, setVal] = useState(0);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const o = new IntersectionObserver(([e]) => {
      if (e.isIntersecting) { setVal(score); o.disconnect(); }
    }, { threshold: 0.2 });
    if (ref.current) o.observe(ref.current);
    return () => o.disconnect();
  }, [score]);
  const c = dangerColor(score);
  const circ = 2 * Math.PI * 76;
  return (
    <div ref={ref} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 14, padding: "20px 0" }}>
      <div style={{ position: "relative", width: 170, height: 170 }}>
        <svg viewBox="0 0 170 170" style={{ transform: "rotate(-90deg)" }}>
          <circle cx="85" cy="85" r="76" fill="none" stroke="#121228" strokeWidth="8" />
          <circle cx="85" cy="85" r="76" fill="none" stroke={c} strokeWidth="8"
            strokeDasharray={circ} strokeDashoffset={circ - (val / 100) * circ}
            strokeLinecap="round" style={{ transition: "stroke-dashoffset 2s cubic-bezier(.25,.46,.45,.94)" }} />
        </svg>
        <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
          <span style={{ fontFamily: "var(--font-playfair), Georgia, serif", fontSize: 44, fontWeight: 900, color: c, lineHeight: 1 }}>{val}</span>
          <span style={{ fontSize: 10, letterSpacing: 3, color: "#3b4768" }}>/100</span>
        </div>
      </div>
      <span style={{ fontSize: 14, fontWeight: 800, letterSpacing: 5, color: c, textTransform: "uppercase" }}>{label}</span>
    </div>
  );
}

function SevBar({ val, label }: { val: number; label: string }) {
  const c = val >= 8 ? "#dc2626" : val >= 5 ? "#d97706" : "#2563eb";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, marginTop: 10 }}>
      <span style={{ fontSize: 11, color: "#3b4768", whiteSpace: "nowrap", minWidth: 52 }}>{label}</span>
      <div style={{ flex: 1, height: 6, background: "#121228", borderRadius: 3, overflow: "hidden" }}>
        <div style={{ width: `${val * 10}%`, height: "100%", background: c, borderRadius: 3, transition: "width 1.2s cubic-bezier(.25,.46,.45,.94)" }} />
      </div>
      <span style={{ fontSize: 13, fontWeight: 800, color: c, minWidth: 22, textAlign: "right" }}>{val}</span>
    </div>
  );
}

function SlideCard({ emoji, title, body, idx }: { emoji: string; title: string; body: string; idx: number }) {
  return (
    <div style={{
      background: "linear-gradient(145deg, #0d0d20, #111130)",
      border: "1px solid #1e1e44", borderRadius: 16, padding: "28px 24px",
      minWidth: 260, maxWidth: 300, flex: "0 0 auto",
      display: "flex", flexDirection: "column", gap: 10,
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ fontSize: 28 }}>{emoji}</span>
        <span style={{ fontSize: 10, color: "#3b4768", letterSpacing: 2, fontWeight: 700 }}>SLIDE {idx + 1}/5</span>
      </div>
      <h3 style={{ fontFamily: "var(--font-playfair), Georgia, serif", fontSize: 17, fontWeight: 800, color: "#e2e8f0", lineHeight: 1.3, margin: 0 }}>{title}</h3>
      <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.65, margin: 0 }}>{body}</p>
    </div>
  );
}

function PrivacyModal({ t, onClose }: { t: UITesti; onClose: () => void }) {
  const pm = t.privacyModal;
  return (
    <div onClick={onClose} style={{
      position: "fixed", inset: 0, zIndex: 1000,
      background: "rgba(0,0,0,0.75)", backdropFilter: "blur(8px)",
      display: "flex", alignItems: "center", justifyContent: "center", padding: 20,
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        background: "#0a0a1a", border: "1px solid #1e1e44", borderRadius: 16,
        maxWidth: 620, width: "100%", maxHeight: "80vh", overflow: "auto", padding: "32px 28px",
      }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
          <h2 style={{ fontFamily: "var(--font-playfair), Georgia, serif", fontSize: 22, fontWeight: 800, color: "#f1f5f9", margin: 0 }}>{pm.title}</h2>
          <button onClick={onClose} style={{ background: "none", border: "1px solid #20203e", borderRadius: 8, color: "#7b8ba8", fontSize: 13, padding: "5px 14px", cursor: "pointer" }}>{pm.close}</button>
        </div>
        {pm.sections.map((s, i) => (
          <div key={i} style={{ marginBottom: 20 }}>
            <h3 style={{ fontSize: 14, fontWeight: 700, color: "#7dd3fc", marginBottom: 6 }}>{s.heading}</h3>
            <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.7, margin: 0 }}>{s.text}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

// Le forme dei dati arrivano da un LLM: tipizzate in modo permissivo,
// la UI gestisce già i campi mancanti con controlli condizionali.
type RisultatoAnalisi = {
  summary?: { text?: string; declared_intent?: string; actual_intent?: string };
  danger_score?: { score?: number; label?: string; explanation?: string };
  rhetoric?: { framework?: string; technique?: string; quote?: string; analysis?: string; severity?: number }[];
  factcheck?: { claim?: string; evidence?: string; source?: string; verdict?: string; verdict_explain?: string }[];
  legal?: { heading?: string; text?: string }[];
  counter_narrative?: { title?: string; text?: string; source?: string }[];
  slides?: { emoji?: string; title?: string; body?: string }[];
};

function Results({ data, t }: { data: RisultatoAnalisi; t: UITesti }) {
  const { summary, danger_score, rhetoric, factcheck, legal, counter_narrative, slides } = data;
  return (
    <>
      {summary && (
        <>
          <SectionHead icon={t.sectionIcons.summary} title={t.sections.summary} />
          <Card>
            <p style={{ fontSize: 15, lineHeight: 1.75, color: "#c8d0e8", margin: 0 }}>{summary.text}</p>
            <div style={{ display: "flex", gap: 12, marginTop: 16, flexWrap: "wrap" }}>
              {summary.declared_intent && (
                <div style={{ flex: 1, minWidth: 200, padding: "10px 14px", background: "#09091a", border: "1px solid #161634", borderRadius: 10 }}>
                  <div style={{ fontSize: 9, fontWeight: 800, letterSpacing: 2, color: "#2d3e58", marginBottom: 4 }}>{t.declaredIntent}</div>
                  <p style={{ fontSize: 13, color: "#7b8ba8", margin: 0, lineHeight: 1.5 }}>{summary.declared_intent}</p>
                </div>
              )}
              {summary.actual_intent && (
                <div style={{ flex: 1, minWidth: 200, padding: "10px 14px", background: "#0c0414", border: "1px solid #2a1640", borderRadius: 10 }}>
                  <div style={{ fontSize: 9, fontWeight: 800, letterSpacing: 2, color: "#7c3aed", marginBottom: 4 }}>{t.actualIntent}</div>
                  <p style={{ fontSize: 13, color: "#c084fc", margin: 0, lineHeight: 1.5 }}>{summary.actual_intent}</p>
                </div>
              )}
            </div>
          </Card>
        </>
      )}

      {danger_score && (
        <>
          <SectionHead icon={t.sectionIcons.danger} title={t.sections.danger} />
          <Card>
            <DangerRing score={danger_score.score ?? 0} label={danger_score.label ?? ""} />
            <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.65, textAlign: "center", margin: 0, maxWidth: 480, marginLeft: "auto", marginRight: "auto" }}>{danger_score.explanation}</p>
          </Card>
        </>
      )}

      {!!rhetoric?.length && (
        <>
          <SectionHead icon={t.sectionIcons.rhetoric} title={t.sections.rhetoric} />
          {rhetoric.map((r, i) => (
            <Card key={i} accent={(r.severity ?? 0) >= 8 ? "#dc2626" : (r.severity ?? 0) >= 5 ? "#d97706" : "#2563eb"}>
              <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: 2, color: "#3b4768", textTransform: "uppercase" }}>{r.framework}</span>
              <h3 style={{ fontSize: 16, fontWeight: 800, color: "#e2e8f0", margin: "4px 0" }}>{r.technique}</h3>
              {r.quote && <blockquote style={{ margin: "12px 0", padding: "10px 16px", borderLeft: "2px solid #20203e", color: "#5b6880", fontStyle: "italic", fontSize: 13, lineHeight: 1.6 }}>&quot;{r.quote}&quot;</blockquote>}
              <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.65, margin: 0 }}>{r.analysis}</p>
              <SevBar val={r.severity ?? 0} label={t.sevLabel} />
            </Card>
          ))}
        </>
      )}

      {!!factcheck?.length && (
        <>
          <SectionHead icon={t.sectionIcons.factcheck} title={t.sections.factcheck} />
          {factcheck.map((f, i) => (
            <Card key={i} accent={verdictColor(f.verdict)}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 10, flexWrap: "wrap" }}>
                <p style={{ fontSize: 14, fontWeight: 700, color: "#c8d0e8", margin: 0, flex: 1, minWidth: 200 }}>&quot;{f.claim}&quot;</p>
                <span style={{ fontSize: 11, fontWeight: 800, letterSpacing: 1, padding: "3px 10px", borderRadius: 6, background: verdictColor(f.verdict) + "18", color: verdictColor(f.verdict), whiteSpace: "nowrap", textTransform: "uppercase" }}>{f.verdict}</span>
              </div>
              <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.65, marginTop: 10, marginBottom: 4 }}>{f.evidence}</p>
              {f.source && <p style={{ fontSize: 11, color: "#3b4768", margin: 0, fontStyle: "italic" }}>{f.source}</p>}
              {f.verdict_explain && <p style={{ fontSize: 12, color: "#5b6880", marginTop: 6, marginBottom: 0 }}>{f.verdict_explain}</p>}
            </Card>
          ))}
        </>
      )}

      {!!legal?.length && (
        <>
          <SectionHead icon={t.sectionIcons.legal} title={t.sections.legal} />
          {legal.map((l, i) => (
            <Card key={i}>
              <h3 style={{ fontSize: 14, fontWeight: 700, color: "#7dd3fc", margin: "0 0 8px" }}>{l.heading}</h3>
              <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.65, margin: 0 }}>{l.text}</p>
            </Card>
          ))}
        </>
      )}

      {!!counter_narrative?.length && (
        <>
          <SectionHead icon={t.sectionIcons.counter} title={t.sections.counter} />
          {counter_narrative.map((cn, i) => (
            <Card key={i} accent="#16a34a">
              <h3 style={{ fontSize: 15, fontWeight: 800, color: "#e2e8f0", margin: "0 0 8px" }}>{cn.title}</h3>
              <p style={{ fontSize: 13, color: "#7b8ba8", lineHeight: 1.65, margin: 0 }}>{cn.text}</p>
              {cn.source && <p style={{ fontSize: 11, color: "#3b4768", marginTop: 6, marginBottom: 0, fontStyle: "italic" }}>{cn.source}</p>}
            </Card>
          ))}
        </>
      )}

      {!!slides?.length && (
        <>
          <SectionHead icon={t.sectionIcons.slides} title={t.sections.slides} />
          <div style={{ display: "flex", gap: 14, overflowX: "auto", paddingBottom: 12 }}>
            {slides.map((s, i) => <SlideCard key={i} emoji={s.emoji ?? ""} title={s.title ?? ""} body={s.body ?? ""} idx={i} />)}
          </div>
        </>
      )}
    </>
  );
}

// ─── MAIN COMPONENT ──────────────────────────────────────────────
export function PolicyDecoder({ quotaIniziale }: { quotaIniziale: Quota }) {
  const [lang, setLang] = useState<Lingua>("it");
  const [inputText, setInputText] = useState("");
  const [urlInput, setUrlInput] = useState("");
  const [inputMode, setInputMode] = useState<"text" | "url">("text");
  const [outputLang, setOutputLang] = useState<"auto" | "en" | "it">("auto");
  const [loading, setLoading] = useState(false);
  const [loadingMsg, setLoadingMsg] = useState("");
  const [error, setError] = useState("");
  const [result, setResult] = useState<RisultatoAnalisi | null>(null);
  const [sourceUrl, setSourceUrl] = useState<string | null>(null);
  const [quota, setQuota] = useState<Quota>(quotaIniziale);
  const [showPrivacy, setShowPrivacy] = useState(false);
  const resultsRef = useRef<HTMLDivElement>(null);
  const t: UITesti = UI[lang];

  const isValidUrl = (s: string) => {
    try { const u = new URL(s); return u.protocol === "http:" || u.protocol === "https:"; }
    catch { return false; }
  };

  const analyze = useCallback(async () => {
    const isUrl = inputMode === "url";
    if (isUrl) {
      if (!urlInput.trim()) { setError(t.errorEmptyUrl); return; }
      if (!isValidUrl(urlInput.trim())) { setError(t.errorInvalidUrl); return; }
    } else {
      if (!inputText.trim()) { setError(t.errorEmpty); return; }
    }
    if (quota.rimanenti <= 0) { setError(t.errorQuotaEsaurita); return; }

    setError("");
    setLoading(true);
    setResult(null);
    setSourceUrl(isUrl ? urlInput.trim() : null);

    try {
      let textToAnalyze = inputText;
      let modalita: "testo" | "url" = "testo";

      if (isUrl) {
        setLoadingMsg(t.urlFetching);
        modalita = "url";
        const r = await fetch("/api/fetch-url", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ url: urlInput.trim() }),
        });
        const d = await r.json();
        if (!r.ok) throw new Error(d?.error || t.errorUrlFetch);
        textToAnalyze = d.testo;
        setInputText(d.testo);
        setSourceUrl(d.fonte);
      }

      setLoadingMsg(t.analyzing);
      const r = await fetch("/api/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ testo: textToAnalyze, modalita, outputLang }),
      });
      const d = await r.json();
      if (!r.ok) throw new Error(d?.error || t.errorGeneric);

      setResult(d.risultato);
      if (d.quota) setQuota(d.quota);
      setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 250);
    } catch (e) {
      setError(e instanceof Error ? e.message : t.errorGeneric);
    } finally {
      setLoading(false);
      setLoadingMsg("");
    }
  }, [inputText, urlInput, inputMode, outputLang, t, quota]);

  const clear = () => { setInputText(""); setUrlInput(""); setResult(null); setError(""); setSourceUrl(null); };

  return (
    <div style={{ minHeight: "100vh", color: "#b8c0d8" }}>
      {showPrivacy && <PrivacyModal t={t} onClose={() => setShowPrivacy(false)} />}
      <header style={{ borderBottom: "1px solid #10102a", background: "#060610e6", backdropFilter: "blur(20px)", position: "sticky", top: 0, zIndex: 50 }}>
        <div style={{ maxWidth: 820, margin: "0 auto", padding: "10px 20px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{
              width: 34, height: 34, borderRadius: 10,
              background: "linear-gradient(135deg, #5B2C6F, #7D3C98)",
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 11, fontWeight: 900, color: "#fff", letterSpacing: 1,
            }}>A</div>
            <div>
              <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
                <span style={{ fontSize: 14, fontWeight: 800, color: "#f1f5f9" }}>{t.appName}</span>
                <span style={{ fontSize: 10, fontWeight: 700, color: "#7D3C98", letterSpacing: 1 }}>by {t.appOrg}</span>
              </div>
              <div style={{ fontSize: 9, color: "#2d3e58", letterSpacing: 2, textTransform: "uppercase" }}>{t.appSub}</div>
            </div>
          </div>
          <button onClick={() => setLang(lang === "en" ? "it" : "en")} style={{
            display: "flex", alignItems: "center", gap: 6,
            background: "#0e0e22", border: "1px solid #20203e", borderRadius: 20,
            padding: "5px 14px", cursor: "pointer", color: "#7dd3fc", fontSize: 13, fontWeight: 700,
          }}>
            {lang === "en" ? "🇬🇧 EN → 🇮🇹 IT" : "🇮🇹 IT → 🇬🇧 EN"}
          </button>
        </div>
      </header>

      <main style={{ maxWidth: 820, margin: "0 auto", padding: "36px 20px 80px" }}>
        {!result && !loading && (
          <div style={{ marginBottom: 32, animation: "fadeUp 0.5s ease" }}>
            <h1 style={{
              fontFamily: "var(--font-playfair), Georgia, serif",
              fontSize: "clamp(24px, 4.5vw, 38px)", fontWeight: 900, lineHeight: 1.15, marginBottom: 12,
              background: "linear-gradient(135deg, #e2e8f0, #C39BD3)",
              WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
            }}>{t.heroTitle}</h1>
            <p style={{ fontSize: 14, color: "#4b5f7a", lineHeight: 1.7, maxWidth: 580 }}>{t.heroDesc}</p>
          </div>
        )}

        {!result && (
          <div style={{ animation: "fadeUp 0.6s ease" }}>
            <div style={{ display: "flex", gap: 0, marginBottom: 12 }}>
              {(["text", "url"] as const).map(mode => (
                <button key={mode} onClick={() => { setInputMode(mode); setError(""); }} style={{
                  padding: "8px 20px", borderRadius: mode === "text" ? "8px 0 0 8px" : "0 8px 8px 0",
                  border: `1.5px solid ${inputMode === mode ? "#5B2C6F" : "#161634"}`,
                  background: inputMode === mode ? "#1a0d2e" : "#09091a",
                  color: inputMode === mode ? "#C39BD3" : "#3b4768",
                  fontSize: 13, fontWeight: 700, cursor: "pointer",
                  borderRight: mode === "text" ? "none" : undefined,
                }}>
                  {mode === "text" ? `📝 ${t.tabText}` : `🔗 ${t.tabUrl}`}
                </button>
              ))}
            </div>

            {inputMode === "text" && (
              <>
                <textarea
                  value={inputText}
                  onChange={(e) => { setInputText(e.target.value); setError(""); }}
                  placeholder={t.placeholder}
                  rows={8}
                  style={{
                    width: "100%", padding: "16px 18px", fontSize: 14, lineHeight: 1.7,
                    background: "#09091a", border: "1.5px solid #161634", borderRadius: 12,
                    color: "#b8c0d8", resize: "vertical", fontFamily: "inherit",
                  }}
                />
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 10, alignItems: "center" }}>
                  <span style={{ fontSize: 11, color: "#2d3e58", marginRight: 2 }}>{t.exampleLabel}:</span>
                  {t.examples.map((ex, i) => (
                    <button key={i} onClick={() => setInputText(ex.text)} style={{
                      padding: "4px 10px", borderRadius: 7, border: "1px solid #161634",
                      background: "#0b0b1c", color: "#C39BD3", fontSize: 11, fontWeight: 600, cursor: "pointer",
                    }}>{ex.label}</button>
                  ))}
                </div>
              </>
            )}

            {inputMode === "url" && (
              <div style={{ flex: 1, position: "relative" }}>
                <span style={{ position: "absolute", left: 14, top: "50%", transform: "translateY(-50%)", fontSize: 16, color: "#3b4768", pointerEvents: "none" }}>🔗</span>
                <input
                  type="url"
                  value={urlInput}
                  onChange={(e) => { setUrlInput(e.target.value); setError(""); }}
                  placeholder={t.urlPlaceholder}
                  style={{
                    width: "100%", padding: "14px 18px 14px 40px", fontSize: 14,
                    background: "#09091a", border: "1.5px solid #161634", borderRadius: 12,
                    color: "#b8c0d8", fontFamily: "inherit",
                  }}
                />
              </div>
            )}

            <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 14, flexWrap: "wrap" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <label style={{ fontSize: 12, color: "#3b4768", fontWeight: 600 }}>{t.outputLangLabel}:</label>
                <select value={outputLang} onChange={(e) => setOutputLang(e.target.value as "auto" | "en" | "it")} style={{
                  padding: "5px 10px", borderRadius: 7, border: "1.5px solid #161634",
                  background: "#09091a", color: "#b8c0d8", fontSize: 12, fontFamily: "inherit",
                }}>
                  <option value="auto">{t.outputLangAuto}</option>
                  <option value="en">English</option>
                  <option value="it">Italiano</option>
                </select>
              </div>
              <button onClick={analyze} disabled={loading || quota.rimanenti <= 0} style={{
                padding: "9px 26px", borderRadius: 9, border: "none",
                background: loading || quota.rimanenti <= 0 ? "#161634" : "linear-gradient(135deg, #5B2C6F, #7D3C98)",
                color: "#fff", fontSize: 14, fontWeight: 800, cursor: loading ? "default" : "pointer",
                display: "flex", alignItems: "center", gap: 8,
                opacity: loading || quota.rimanenti <= 0 ? 0.6 : 1,
              }}>
                {loading && <span style={{ display: "inline-block", width: 14, height: 14, border: "2px solid #fff3", borderTopColor: "#fff", borderRadius: "50%", animation: "spin 0.7s linear infinite" }} />}
                {loading ? loadingMsg || t.analyzing : t.analyzeBtn}
              </button>
              <span style={{ fontSize: 11, color: "#2d3e58" }}>
                {quota.rimanenti}/{quota.limite} {t.quotaLabel}
              </span>
            </div>

            {error && (
              <div style={{ marginTop: 12, padding: "9px 14px", borderRadius: 9, background: "#14050a", border: "1px solid #dc262625", color: "#fca5a5", fontSize: 13 }}>{error}</div>
            )}
          </div>
        )}

        {loading && (
          <div style={{ marginTop: 16 }}>
            {[90, 200, 120, 160, 100, 140].map((h, i) => (
              <div key={i} style={{
                height: h, marginBottom: 12, borderRadius: 12,
                background: "linear-gradient(90deg, #0b0b1c 25%, #12122a 50%, #0b0b1c 75%)",
                backgroundSize: "200% 100%", animation: "shimmer 1.4s ease-in-out infinite",
              }} />
            ))}
          </div>
        )}

        {result && (
          <div ref={resultsRef} style={{ animation: "fadeUp 0.5s ease" }}>
            <button onClick={clear} style={{
              padding: "7px 16px", borderRadius: 8, border: "1px solid #161634",
              background: "#0b0b1c", color: "#C39BD3", fontSize: 12, fontWeight: 700,
              cursor: "pointer", marginBottom: 18, display: "flex", alignItems: "center", gap: 5,
            }}>← {t.clearBtn}</button>

            {sourceUrl && (
              <div style={{ padding: "8px 14px", background: "#0c0414", border: "1px solid #2a1640", borderRadius: 10, marginBottom: 10, display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontSize: 14 }}>🔗</span>
                <div>
                  <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: 2, color: "#7D3C98", textTransform: "uppercase" }}>{t.urlSource}</span>
                  <a href={sourceUrl} target="_blank" rel="noopener noreferrer" style={{ display: "block", fontSize: 12, color: "#C39BD3", wordBreak: "break-all", textDecoration: "none" }}>{sourceUrl}</a>
                </div>
              </div>
            )}

            <div style={{ padding: "12px 16px", background: "#08081a", border: "1px solid #10102a", borderRadius: 10, marginBottom: 22, maxHeight: 100, overflow: "hidden", position: "relative" }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 2, color: "#2d3e58", marginBottom: 4, textTransform: "uppercase" }}>{t.analyzedText}</div>
              <p style={{ fontSize: 12, color: "#3b4768", lineHeight: 1.5, margin: 0, whiteSpace: "pre-wrap" }}>{inputText.slice(0, 400)}{inputText.length > 400 ? "…" : ""}</p>
              <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 30, background: "linear-gradient(transparent, #08081a)" }} />
            </div>

            <Results data={result} t={t} />
          </div>
        )}

        <div style={{ marginTop: 56, paddingTop: 20, borderTop: "1px solid #10102a", textAlign: "center" }}>
          <p style={{ fontSize: 11, color: "#1e2a3a", lineHeight: 1.5 }}>{t.poweredBy}</p>
          <p style={{ fontSize: 10, color: "#141e30", marginTop: 3 }}>{t.disclaimer}</p>
          <div style={{ marginTop: 10, display: "flex", justifyContent: "center", gap: 16, alignItems: "center" }}>
            <button onClick={() => setShowPrivacy(true)} style={{ background: "none", border: "none", color: "#3b4768", fontSize: 11, cursor: "pointer", textDecoration: "underline", fontFamily: "inherit" }}>
              {t.privacyLink}
            </button>
            <span style={{ color: "#10102a" }}>·</span>
            <a href="https://www.associazionetransgenere.org" target="_blank" rel="noopener noreferrer" style={{ color: "#3b4768", fontSize: 11, textDecoration: "none" }}>
              {t.appOrgFull}
            </a>
          </div>
          <p style={{ fontSize: 9, color: "#0e1520", marginTop: 8 }}>© 2026 ACET · Milano, Italia</p>
        </div>
      </main>
    </div>
  );
}
