# 🛡️ Policy Decoder

**Trans Rights Media Literacy & Counter-Propaganda Tool**

Decode anti-trans propaganda with science and evidence. Paste any text — political statements, policy documents, news articles, social media posts — and get a rigorous, evidence-based analysis.

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![React](https://img.shields.io/badge/React-18-61DAFB.svg)
![Vite](https://img.shields.io/badge/Vite-6-646CFF.svg)

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

- 🌐 **Bilingual interface** (English / Italian) with language switcher
- 🎯 **Output language control**: auto-detect, force English, or force Italian
- 📝 **Pre-loaded examples** to test immediately
- 🎨 **Dark editorial design** with Playfair Display + DM Sans typography
- ⚡ **Powered by Claude API** (Sonnet 4) with structured JSON output

---

## 🚀 Quick Start

### Prerequisites

- [Node.js](https://nodejs.org/) 18+ installed
- An Anthropic API key (the app runs inside Claude.ai artifacts where the key is provided automatically — for standalone deployment, see below)

### Local Development

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/policy-decoder.git
cd policy-decoder

# Install dependencies
npm install

# Start dev server
npm run dev
```

Open `http://localhost:5173` in your browser.

### Build for Production

```bash
npm run build
```

Output will be in the `dist/` folder.

---

## 🌍 Deploy to Vercel (Recommended — Free)

### Option A: One-Click Deploy

1. Push this repo to GitHub
2. Go to [vercel.com](https://vercel.com) and sign in with GitHub
3. Click **"Add New Project"**
4. Import your `policy-decoder` repository
5. Vercel auto-detects Vite — just click **"Deploy"**
6. Your site is live at `https://policy-decoder.vercel.app` (or your custom domain)

### Option B: Vercel CLI

```bash
# Install Vercel CLI
npm i -g vercel

# Deploy
vercel

# Follow the prompts — it auto-detects Vite
```

### Option C: Netlify

```bash
# Build first
npm run build

# Deploy the dist folder to Netlify
# Or connect your GitHub repo at netlify.com
```

Netlify settings:
- **Build command**: `npm run build`
- **Publish directory**: `dist`

---

## 📝 How the API Works

The app calls the Anthropic Messages API directly from the browser. When running inside a **Claude.ai artifact**, the API key is handled automatically — no configuration needed.

For **standalone deployment** outside Claude.ai, you would need to:

1. Set up a small backend proxy (e.g., Vercel Serverless Function, Cloudflare Worker) that holds your API key
2. Modify the `fetch` call in `src/App.jsx` to point to your proxy endpoint

Example Vercel serverless function (`api/analyze.js`):

```javascript
export default async function handler(req, res) {
  const response = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": process.env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify(req.body),
  });
  const data = await response.json();
  res.status(response.status).json(data);
}
```

Then update the fetch URL in `App.jsx`:
```javascript
const resp = await fetch("/api/analyze", { ... });
```

---

## 🔬 Scientific Sources

The system prompt references these key peer-reviewed sources:

| Source | What it shows |
|--------|-------------|
| **CCES Scientific Review (2022)** | No firm basis for performance advantage after 12mo testosterone suppression |
| **Harper et al. (2021, BJSM)** | Systematic review on HRT effects on body composition |
| **Harper (2015)** | Age-graded performance equivalence in trans women runners |
| **Roberts et al. (2021)** | Military fitness data showing significant reductions post-HRT |
| **Jones et al. (2016, Sports Medicine)** | Systematic review: athletic advantage is overinterpreted |
| **WPATH SOC-8 (2022)** | Standards of Care for transgender health |
| **Karkazis et al.** | Testosterone not predictive of athletic performance |

---

## 📚 Propaganda Analysis Frameworks

| Framework | Focus |
|-----------|-------|
| **Ellul** | Integral propaganda: agitation vs integration, vertical vs horizontal |
| **Chomsky & Herman** | Manufacturing Consent: 5 filters (ownership, advertising, sourcing, flak, ideology) |
| **Cialdini** | Weapons of influence: reciprocity, commitment, social proof, authority, liking, scarcity |
| **Arendt** | Banality of evil: normalization, us-vs-them, obedience, critical thinking erosion |
| **Fairclough** | Critical Discourse Analysis: power relations, ideology in language, nominalization |

---

## 🤝 Contributing

Contributions welcome! Areas where help is needed:

- **Additional languages** (Spanish, French, German, Portuguese)
- **More example texts** for different countries and contexts
- **Backend proxy** for standalone deployment
- **Accessibility improvements**
- **Additional scientific sources** and legal frameworks

Please open an issue or PR.

---

## ⚖️ License

MIT — Use it, fork it, share it. Built for the community.

---

## 💜 Solidarity

This tool exists because trans rights are human rights. It is built for advocates, journalists, educators, and anyone who needs evidence-based tools to counter misinformation.

> *"The tool's strength is precision, not overstatement."*

Trans people have always existed. Trans people will always exist. This tool helps ensure the facts are heard.

🏳️‍⚧️
