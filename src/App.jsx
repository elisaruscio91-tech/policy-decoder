import { useState, useRef, useEffect, useCallback } from "react";

// ─── i18n ────────────────────────────────────────────────────────
const UI = {
  en: {
    appName: "Policy Decoder",
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
      summary: "Summary",
      danger: "Danger Score",
      rhetoric: "Rhetorical Techniques",
      factcheck: "Fact-Check",
      legal: "Legal Context",
      counter: "Counter-Narrative",
      slides: "Story Slides",
    },
    sectionIcons: { summary: "📋", danger: "🚨", rhetoric: "🔍", factcheck: "🔬", legal: "⚖️", counter: "💪", slides: "📱" },
    errorGeneric: "Analysis failed. Please try again.",
    errorEmpty: "Please paste some text to analyze.",
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
  },
  it: {
    appName: "Policy Decoder",
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
      summary: "Sommario",
      danger: "Danger Score",
      rhetoric: "Tecniche Retoriche",
      factcheck: "Fact-Check",
      legal: "Contesto Legale",
      counter: "Counter-Narrative",
      slides: "Story Slides",
    },
    sectionIcons: { summary: "📋", danger: "🚨", rhetoric: "🔍", factcheck: "🔬", legal: "⚖️", counter: "💪", slides: "📱" },
    errorGeneric: "Analisi fallita. Riprova.",
    errorEmpty: "Incolla del testo da analizzare.",
    poweredBy: "Alimentato dalle evidenze. Costruito per l'advocacy, non per la propaganda.",
    disclaimer: "Strumento educativo e di media literacy. Non costituisce consulenza legale.",
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
  },
};

// ─── SYSTEM PROMPT ───────────────────────────────────────────────
const buildSystemPrompt = (outputLang) => `You are an expert analyst in media literacy, fact-checking, and evidence-based advocacy, specializing in the protection of transgender and non-binary people's rights.

TASK: Analyze the provided text and produce a structured analysis.

SCIENTIFIC RIGOR:
- Cite peer-reviewed studies, meta-analyses, guidelines from recognized scientific societies (Endocrine Society, WPATH SOC-8, APA, AAP, WHO).
- Format: [Author et al., Year, Journal]
- Distinguish strong evidence (RCT, meta-analyses), moderate (observational), expert opinion.
- When evidence is limited, say so explicitly. Never invent citations.

KEY SCIENTIFIC SOURCES TO REFERENCE WHEN RELEVANT:
- CCES Scientific Review (2022): comprehensive review finding no firm basis for performance advantage after 12mo testosterone suppression
- Harper et al. (2021, BJSM): systematic review on HRT effects on body composition
- Harper (2015): age-graded performance equivalence in trans women runners
- Roberts et al. (2021): military fitness data showing significant reductions
- Jones et al. (2016, Sports Medicine): systematic review on sport participation
- WPATH SOC-8 (2022): Standards of Care
- Karkazis et al.: testosterone not predictive of athletic performance

PROPAGANDA ANALYSIS FRAMEWORKS:
- Ellul — integral propaganda (agitation/integration, vertical/horizontal)
- Chomsky & Herman — Manufacturing Consent (5 filters: ownership, advertising, sourcing, flak, ideology)
- Cialdini — weapons of influence (reciprocity, commitment, social proof, authority, liking, scarcity)
- Arendt — banality of evil (normalization, us-vs-them, obedience, critical thinking erosion)
- Fairclough — Critical Discourse Analysis (power relations, ideology in language, intertextuality, nominalization, passive agency)

LEGAL FRAMEWORKS TO REFERENCE WHEN RELEVANT:
- Yogyakarta Principles (2006, +10 2017), Principle 14
- EU Charter of Fundamental Rights, Art. 21
- ECHR: Goodwin v. UK (2002), AP Garçon Nicot v. France (2017)
- Italian law: L. 164/1982, Corte Cost. 221/2015
- CAS: Semenya v. World Athletics (2019)

RULES:
- Never minimize anti-trans propaganda severity for false "balance" — bothsidesism is itself propaganda.
- Human rights are not opinions. Trans existence is not a "debate."
- Never use deadnames or misgender.
- Be precise: the tool's strength is accuracy, not overstatement.

OUTPUT LANGUAGE: ${outputLang === "auto" ? "Respond in the SAME language as the input text." : outputLang === "en" ? "Respond in English." : "Rispondi in italiano."}

OUTPUT FORMAT: Respond ONLY with valid JSON. No markdown, no backticks, no preamble. Exact structure:

{
  "summary": {
    "text": "2-3 sentence summary: what the text says and its real intent",
    "declared_intent": "one line",
    "actual_intent": "one line"
  },
  "danger_score": {
    "score": 0-100,
    "label": "Low|Medium|High|Critical",
    "explanation": "why this score"
  },
  "rhetoric": [
    {
      "framework": "academic framework name",
      "technique": "technique name",
      "quote": "direct quote from analyzed text that exemplifies this",
      "analysis": "detailed explanation of how the technique works here",
      "severity": 1-10
    }
  ],
  "factcheck": [
    {
      "claim": "the verifiable claim extracted from the text",
      "evidence": "scientific evidence for or against, with citations",
      "source": "[Author, Year, Journal] format",
      "verdict": "True|Partially true|Misleading|False|Unverifiable",
      "verdict_explain": "one-line explanation"
    }
  ],
  "legal": [
    {
      "heading": "area of law",
      "text": "relevant norms, rulings, principles with specific references"
    }
  ],
  "counter_narrative": [
    {
      "title": "key point title",
      "text": "explanation with evidence",
      "source": "[Author, Year] or legal reference"
    }
  ],
  "slides": [
    {
      "emoji": "single emoji",
      "title": "punchy headline for social media",
      "body": "2-3 sentences, accessible and shareable for Instagram/Twitter stories"
    }
  ]
}

Generate 3-6 rhetoric items, 3-6 factcheck items, 3-5 counter_narrative items, exactly 5 slides, 2-4 legal items.`;

// ─── HELPERS ─────────────────────────────────────────────────────
const dangerColor = (s) => s >= 80 ? "#dc2626" : s >= 60 ? "#ea580c" : s >= 40 ? "#d97706" : s >= 20 ? "#2563eb" : "#16a34a";

const verdictColor = (v) => {
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
function LangSwitch({ lang, setLang }) {
  return (
    <button onClick={() => setLang(lang === "en" ? "it" : "en")} style={{
      display: "flex", alignItems: "center", gap: 6,
      background: "#0e0e22", border: "1px solid #20203e", borderRadius: 20,
      padding: "5px 14px", cursor: "pointer", color: "#7dd3fc", fontSize: 13, fontWeight: 700,
      transition: "all 0.15s",
    }}>
      {lang === "en" ? "🇬🇧 EN → 🇮🇹 IT" : "🇮🇹 IT → 🇬🇧 EN"}
    </button>
  );
}

function SectionHead({ icon, title }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 18, marginTop: 40 }}>
      <span style={{ fontSize: 22 }}>{icon}</span>
      <h2 style={{ fontFamily: "'Playfair Display', Georgia, serif", fontSize: 22, fontWeight: 800, margin: 0, color: "#f1f5f9" }}>{title}</h2>
    </div>
  );
}

function Card({ children, accent }) {
  return (
    <div style={{
      background: "#0b0b1c", border: "1px solid #181836",
      borderLeft: accent ? `3px solid ${accent}` : undefined,
      borderRadius: 12, padding: "20px 22px", marginBottom: 12,
    }}>{children}</div>
  );
}

function DangerRing({ score, label }) {
  const [val, setVal] = useState(0);
  const ref = useRef(null);
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
          <span style={{ fontFamily: "'Playfair Display', Georgia, serif", fontSize: 44, fontWeight: 900, color: c, lineHeight: 1 }}>{val}</span>
          <span style={{ fontSize: 10, letterSpacing: 3, color: "#3b4768" }}>/100</span>
        </div>
      </div>
      <span style={{ fontSize: 14, fontWeight: 800, letterSpacing: 5, color: c, textTransform: "uppercase" }}>{label}</span>
    </div>
  );
}

function SevBar({ value }) {
  const clr = value >= 8 ? "#dc2626" : value >= 6 ? "#ea580c" : value >= 4 ? "#d97706" : "#2563eb";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 6 }}>
      <div style={{ flex: 1, height: 4, background: "#121228", borderRadius: 2, overflow: "hidden" }}>
        <div style={{ width: `${(value / 10) * 100}%`, height: "100%", background: clr, borderRadius: 2 }} />
      </div>
      <span style={{ fontSize: 12, fontWeight: 700, color: clr }}>{value}/10</span>
    </div>
  );
}

function SlideCard({ n, emoji, title, body }) {
  const colors = ["#3b82f6", "#8b5cf6", "#06b6d4", "#f59e0b", "#ef4444"];
  const c = colors[(n - 1) % colors.length];
  return (
    <div style={{
      background: `linear-gradient(145deg, ${c}0c, ${c}04)`, border: `1px solid ${c}20`,
      borderRadius: 14, padding: 20, minWidth: 260, maxWidth: 290, flex: "0 0 auto",
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span style={{ fontSize: 10, fontWeight: 800, letterSpacing: 3, color: c }}>SLIDE {n}</span>
        <span style={{ fontSize: 20 }}>{emoji}</span>
      </div>
      <h4 style={{ fontSize: 14, fontWeight: 800, color: "#e2e8f0", lineHeight: 1.3, margin: "0 0 6px" }}>{title}</h4>
      <p style={{ fontSize: 12, color: "#7b859e", lineHeight: 1.65, margin: 0 }}>{body}</p>
    </div>
  );
}

// ─── RESULTS VIEW ────────────────────────────────────────────────
function Results({ data, t }) {
  const ic = t.sectionIcons;
  const lb = t.sections;
  if (!data) return null;
  return (
    <div>
      <SectionHead icon={ic.summary} title={lb.summary} />
      <Card>
        <p style={{ fontSize: 15, lineHeight: 1.8, color: "#b8c0d8", margin: 0 }}>{data.summary?.text}</p>
        {data.summary?.declared_intent && (
          <div style={{ marginTop: 14, padding: "10px 14px", background: "#090918", borderRadius: 8, borderLeft: "3px solid #3b82f6" }}>
            <div style={{ fontSize: 11, fontWeight: 800, letterSpacing: 2, color: "#3b82f6", marginBottom: 3 }}>{t.declaredIntent}</div>
            <p style={{ margin: 0, fontSize: 13, color: "#7b859e" }}>{data.summary.declared_intent}</p>
          </div>
        )}
        {data.summary?.actual_intent && (
          <div style={{ marginTop: 8, padding: "10px 14px", background: "#090918", borderRadius: 8, borderLeft: "3px solid #dc2626" }}>
            <div style={{ fontSize: 11, fontWeight: 800, letterSpacing: 2, color: "#dc2626", marginBottom: 3 }}>{t.actualIntent}</div>
            <p style={{ margin: 0, fontSize: 13, color: "#7b859e" }}>{data.summary.actual_intent}</p>
          </div>
        )}
      </Card>

      <SectionHead icon={ic.danger} title={lb.danger} />
      <Card>
        <DangerRing score={data.danger_score?.score || 0} label={data.danger_score?.label || "—"} />
        <p style={{ textAlign: "center", color: "#6b7a94", fontSize: 13, lineHeight: 1.7, maxWidth: 500, margin: "0 auto" }}>{data.danger_score?.explanation}</p>
      </Card>

      <SectionHead icon={ic.rhetoric} title={lb.rhetoric} />
      {(data.rhetoric || []).map((r, i) => (
        <Card key={i} accent={r.severity >= 8 ? "#dc2626" : r.severity >= 6 ? "#ea580c" : "#d97706"}>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 10 }}>
            <span style={{ padding: "2px 9px", borderRadius: 6, background: "#0e0e28", color: "#7dd3fc", fontSize: 11, fontWeight: 700 }}>{r.framework}</span>
            <span style={{ padding: "2px 9px", borderRadius: 6, background: "#18120a", color: "#fbbf24", fontSize: 11, fontWeight: 700 }}>{r.technique}</span>
          </div>
          {r.quote && (
            <blockquote style={{ margin: "0 0 10px", padding: "8px 14px", borderLeft: "2px solid #222244", background: "#08081a", borderRadius: "0 8px 8px 0", fontSize: 13, fontStyle: "italic", color: "#6b7a94", lineHeight: 1.6 }}>
              "{r.quote}"
            </blockquote>
          )}
          <p style={{ fontSize: 14, lineHeight: 1.7, color: "#9ca8c0", margin: 0 }}>{r.analysis}</p>
          <SevBar value={r.severity} />
        </Card>
      ))}

      <SectionHead icon={ic.factcheck} title={lb.factcheck} />
      {(data.factcheck || []).map((f, i) => {
        const vc = verdictColor(f.verdict);
        return (
          <Card key={i} accent={vc}>
            <div style={{ fontSize: 10, fontWeight: 800, letterSpacing: 2, color: "#3b4768", marginBottom: 4 }}>CLAIM</div>
            <p style={{ fontSize: 14, fontWeight: 700, color: "#dce2f0", margin: "0 0 10px", lineHeight: 1.4 }}>{f.claim}</p>
            <div style={{ fontSize: 10, fontWeight: 800, letterSpacing: 2, color: "#3b4768", marginBottom: 3 }}>EVIDENCE</div>
            <p style={{ fontSize: 13, color: "#7b859e", margin: "0 0 6px", lineHeight: 1.6 }}>{f.evidence}</p>
            {f.source && <p style={{ fontSize: 11, color: "#3b82f6", margin: "0 0 10px" }}>{f.source}</p>}
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              <span style={{ padding: "3px 12px", borderRadius: 16, fontSize: 12, fontWeight: 800, color: vc, background: `${vc}12`, border: `1.5px solid ${vc}30` }}>{f.verdict}</span>
              <span style={{ fontSize: 12, color: "#4b5f7a" }}>{f.verdict_explain}</span>
            </div>
          </Card>
        );
      })}

      <SectionHead icon={ic.legal} title={lb.legal} />
      {(data.legal || []).map((l, i) => (
        <Card key={i}>
          <h4 style={{ fontSize: 14, fontWeight: 800, color: "#7dd3fc", margin: "0 0 6px" }}>{l.heading}</h4>
          <p style={{ fontSize: 13, color: "#7b859e", lineHeight: 1.7, margin: 0 }}>{l.text}</p>
        </Card>
      ))}

      <SectionHead icon={ic.counter} title={lb.counter} />
      {(data.counter_narrative || []).map((c, i) => (
        <Card key={i} accent="#16a34a">
          <h4 style={{ fontSize: 14, fontWeight: 800, color: "#4ade80", margin: "0 0 6px" }}>{c.title}</h4>
          <p style={{ fontSize: 13, color: "#7b859e", lineHeight: 1.7, margin: "0 0 4px" }}>{c.text}</p>
          {c.source && <p style={{ fontSize: 11, color: "#3b82f6", margin: 0 }}>{c.source}</p>}
        </Card>
      ))}

      <SectionHead icon={ic.slides} title={lb.slides} />
      <div style={{ display: "flex", gap: 12, overflowX: "auto", paddingBottom: 10 }}>
        {(data.slides || []).map((s, i) => (
          <SlideCard key={i} n={i + 1} emoji={s.emoji} title={s.title} body={s.body} />
        ))}
      </div>
    </div>
  );
}

// ─── MAIN APP ────────────────────────────────────────────────────
export default function App() {
  const [lang, setLang] = useState("en");
  const [inputText, setInputText] = useState("");
  const [outputLang, setOutputLang] = useState("auto");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const resultsRef = useRef(null);
  const t = UI[lang];

  const analyze = useCallback(async () => {
    if (!inputText.trim()) { setError(t.errorEmpty); return; }
    setError("");
    setLoading(true);
    setResult(null);

    try {
      const resp = await fetch("https://api.anthropic.com/v1/messages", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "claude-sonnet-4-20250514",
          max_tokens: 4096,
          system: buildSystemPrompt(outputLang),
          messages: [{ role: "user", content: `Analyze this text:\n\n---\n${inputText}\n---` }],
        }),
      });
      const data = await resp.json();
      if (!resp.ok) throw new Error(data?.error?.message || "API error " + resp.status);

      const raw = (data.content || []).map(b => b.text || "").join("");
      let parsed;
      try {
        parsed = JSON.parse(raw.replace(/```json\s*/g, "").replace(/```\s*/g, "").trim());
      } catch {
        const m = raw.match(/\{[\s\S]*\}/);
        if (m) parsed = JSON.parse(m[0]);
        else throw new Error("Failed to parse structured response");
      }
      setResult(parsed);
      setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 250);
    } catch (e) {
      setError(e.message || t.errorGeneric);
    } finally {
      setLoading(false);
    }
  }, [inputText, outputLang, t]);

  const clear = () => { setInputText(""); setResult(null); setError(""); };

  return (
    <div style={{ minHeight: "100vh", background: "#060610", color: "#b8c0d8", fontFamily: "'DM Sans', system-ui, sans-serif" }}>
      <link href="https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,400;9..40,500;9..40,700;9..40,800&family=Playfair+Display:wght@700;800;900&display=swap" rel="stylesheet" />
      <style>{`
        * { box-sizing: border-box; margin: 0; }
        ::-webkit-scrollbar { width: 5px; height: 5px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: #1a1a38; border-radius: 3px; }
        textarea:focus, select:focus { outline: none; border-color: #3b82f680 !important; box-shadow: 0 0 0 3px #3b82f618; }
        @keyframes spin { to { transform: rotate(360deg); } }
        @keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
        @keyframes fadeUp { from { opacity:0; transform: translateY(12px); } to { opacity:1; transform: translateY(0); } }
      `}</style>

      {/* HEADER */}
      <header style={{ borderBottom: "1px solid #10102a", background: "#060610e6", backdropFilter: "blur(20px)", position: "sticky", top: 0, zIndex: 50 }}>
        <div style={{ maxWidth: 820, margin: "0 auto", padding: "10px 20px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ width: 30, height: 30, borderRadius: 8, background: "linear-gradient(135deg, #3b82f6, #a855f7)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 15 }}>🛡️</div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 800, color: "#f1f5f9" }}>{t.appName}</div>
              <div style={{ fontSize: 9, color: "#2d3e58", letterSpacing: 2, textTransform: "uppercase" }}>{t.appSub}</div>
            </div>
          </div>
          <LangSwitch lang={lang} setLang={setLang} />
        </div>
      </header>

      <main style={{ maxWidth: 820, margin: "0 auto", padding: "36px 20px 80px" }}>
        {/* HERO (only when no results) */}
        {!result && !loading && (
          <div style={{ marginBottom: 32, animation: "fadeUp 0.5s ease" }}>
            <h1 style={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: "clamp(24px, 4.5vw, 38px)", fontWeight: 900, lineHeight: 1.15, marginBottom: 12,
              background: "linear-gradient(135deg, #e2e8f0, #7dd3fc)",
              WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent",
            }}>{t.heroTitle}</h1>
            <p style={{ fontSize: 14, color: "#4b5f7a", lineHeight: 1.7, maxWidth: 580 }}>{t.heroDesc}</p>
          </div>
        )}

        {/* INPUT AREA */}
        {!result && (
          <div style={{ animation: "fadeUp 0.6s ease" }}>
            <textarea
              value={inputText}
              onChange={(e) => { setInputText(e.target.value); setError(""); }}
              placeholder={t.placeholder}
              rows={8}
              style={{
                width: "100%", padding: "16px 18px", fontSize: 14, lineHeight: 1.7,
                background: "#09091a", border: "1.5px solid #161634", borderRadius: 12,
                color: "#b8c0d8", resize: "vertical", fontFamily: "inherit", transition: "border-color 0.2s, box-shadow 0.2s",
              }}
            />
            {/* Example buttons */}
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 10, alignItems: "center" }}>
              <span style={{ fontSize: 11, color: "#2d3e58", marginRight: 2 }}>{t.exampleLabel}:</span>
              {t.examples.map((ex, i) => (
                <button key={i} onClick={() => setInputText(ex.text)} style={{
                  padding: "4px 10px", borderRadius: 7, border: "1px solid #161634",
                  background: "#0b0b1c", color: "#5b9bd5", fontSize: 11, fontWeight: 600, cursor: "pointer",
                  transition: "background 0.15s",
                }}>{ex.label}</button>
              ))}
            </div>

            {/* Controls row */}
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 14, flexWrap: "wrap" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <label style={{ fontSize: 12, color: "#3b4768", fontWeight: 600 }}>{t.outputLangLabel}:</label>
                <select value={outputLang} onChange={(e) => setOutputLang(e.target.value)} style={{
                  padding: "5px 10px", borderRadius: 7, border: "1.5px solid #161634",
                  background: "#09091a", color: "#b8c0d8", fontSize: 12, fontFamily: "inherit", transition: "border-color 0.2s",
                }}>
                  <option value="auto">{t.outputLangAuto}</option>
                  <option value="en">English</option>
                  <option value="it">Italiano</option>
                </select>
              </div>
              <button onClick={analyze} disabled={loading} style={{
                padding: "9px 26px", borderRadius: 9, border: "none",
                background: loading ? "#161634" : "linear-gradient(135deg, #3b82f6, #7c3aed)",
                color: "#fff", fontSize: 14, fontWeight: 800, cursor: loading ? "default" : "pointer",
                display: "flex", alignItems: "center", gap: 8, transition: "opacity 0.2s",
                opacity: loading ? 0.6 : 1,
              }}>
                {loading && <span style={{ display: "inline-block", width: 14, height: 14, border: "2px solid #fff3", borderTopColor: "#fff", borderRadius: "50%", animation: "spin 0.7s linear infinite" }} />}
                {loading ? t.analyzing : t.analyzeBtn}
              </button>
            </div>

            {error && (
              <div style={{ marginTop: 12, padding: "9px 14px", borderRadius: 9, background: "#14050a", border: "1px solid #dc262625", color: "#fca5a5", fontSize: 13 }}>{error}</div>
            )}
          </div>
        )}

        {/* LOADING SKELETON */}
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

        {/* RESULTS */}
        {result && (
          <div ref={resultsRef} style={{ animation: "fadeUp 0.5s ease" }}>
            <button onClick={clear} style={{
              padding: "7px 16px", borderRadius: 8, border: "1px solid #161634",
              background: "#0b0b1c", color: "#5b9bd5", fontSize: 12, fontWeight: 700,
              cursor: "pointer", marginBottom: 18, display: "flex", alignItems: "center", gap: 5,
            }}>← {t.clearBtn}</button>

            {/* Source text preview */}
            <div style={{
              padding: "12px 16px", background: "#08081a", border: "1px solid #10102a",
              borderRadius: 10, marginBottom: 22, maxHeight: 100, overflow: "hidden", position: "relative",
            }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 2, color: "#2d3e58", marginBottom: 4, textTransform: "uppercase" }}>{t.analyzedText}</div>
              <p style={{ fontSize: 12, color: "#3b4768", lineHeight: 1.5, margin: 0, whiteSpace: "pre-wrap" }}>{inputText.slice(0, 400)}{inputText.length > 400 ? "…" : ""}</p>
              <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 30, background: "linear-gradient(transparent, #08081a)" }} />
            </div>

            <Results data={result} t={t} />
          </div>
        )}

        {/* FOOTER */}
        <div style={{ marginTop: 56, paddingTop: 20, borderTop: "1px solid #10102a", textAlign: "center" }}>
          <p style={{ fontSize: 11, color: "#1e2a3a", lineHeight: 1.5 }}>{t.poweredBy}</p>
          <p style={{ fontSize: 10, color: "#141e30", marginTop: 3 }}>{t.disclaimer}</p>
        </div>
      </main>
    </div>
  );
}
