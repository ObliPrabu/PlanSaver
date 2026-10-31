// PlanSaver AI helper: a tiny Cloudflare Worker that holds the Gemini API key so the app never does.
// The app sends a short summary of the search plus the plans it already found; this asks Gemini to pick from THOSE plans
// only and returns the answer as text. The key lives only in the Worker's secret settings (GEMINI_API_KEY), never in the
// app, in GitHub or in this file.

const MAX_BODY = 8000;   // bytes of JSON accepted
const MAX_PLANS = 15;
const SYSTEM =
  "You help someone compare US cell-phone plans. Use only the plans listed in the message; never invent plans, prices or deals. " +
  "Pick up to 3 and explain briefly, in plain words, why each fits (price, data, deal). If none fit, say so. " +
  "Remind them the prices are unverified and to confirm on the carrier's own site. Keep it under 120 words.";

// Strings come from the app, so strip line breaks and cap the length before they go into the prompt.
const clean = (s, n = 120) => String(s ?? "").replace(/[\r\n]+/g, " ").trim().slice(0, n);
const num = (v) => (typeof v === "number" && isFinite(v) && v >= 0 ? v : null);

export function buildPrompt(body) {
  if (!body || !Array.isArray(body.plans) || body.plans.length === 0) return null;
  const lines = Math.min(Math.max(Math.trunc(num(body.lines) ?? 1), 1), 10);
  const data = num(body.dataNeedGb);
  const current = num(body.currentMonthly);
  const plans = body.plans.slice(0, MAX_PLANS).map((p, i) => {
    const monthly = num(p.monthly);
    const gb = num(p.dataGb);
    return `${i + 1}. ${clean(p.carrier, 40)} ${clean(p.name, 60)}: ` +
      `${monthly === null ? "price unknown" : "$" + monthly.toFixed(2) + " a month"}, ` +
      `${gb === null ? "unlimited data" : gb + " GB"}` +
      (p.deal ? `, deal: ${clean(p.deal, 120)}` : "");
  });
  // Data wanted on each line when the person set them separately (a number of GB, or null for unlimited).
  const needs = Array.isArray(body.lineDataNeeds) && body.lineDataNeeds.length > 0
    ? body.lineDataNeeds.slice(0, 10).map((n, i) => `line ${i + 1}: ${num(n) === null ? "unlimited" : num(n) + " GB"}`).join(", ")
    : null;
  return [
    `I need a plan for ${lines} line${lines === 1 ? "" : "s"}.`,
    needs ? `Data I want on each line: ${needs}.` : data === null ? "I want unlimited data." : `I need about ${data} GB of data.`,
    current === null ? "" : `I pay about $${current.toFixed(0)} a month now.`,
    body.includePostpaid === false ? "I only want prepaid plans, not the big carriers' postpaid plans."
      : body.includePostpaid === true ? "I am open to big-carrier and cable plans as well." : "",
    body.freePhoneOnly ? "I only want carriers that advertise a free or discounted phone." : "",
    body.phone ? `I am interested in this phone: ${clean(body.phone, 60)}.` : "",
    body.includeOtherPhones ? "Deals for newer phones from the same brand are fine too." : "",
    body.tradeInPhone ? `I can trade in my old phone: ${clean(body.tradeInPhone, 60)}.` : "",
    "Here are the plans the app found (prices are for all my lines together):",
    ...plans,
    "Which should I look at first?",
  ].filter(Boolean).join("\n");
}

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return json({ error: "Use POST." }, 405);
    const raw = await request.text();
    if (raw.length > MAX_BODY) return json({ error: "Request too large." }, 413);
    let body;
    try { body = JSON.parse(raw); } catch { return json({ error: "Bad JSON." }, 400); }
    const prompt = buildPrompt(body);
    if (!prompt) return json({ error: "Missing search." }, 400);
    // Secret names are case-sensitive; accept the one typed with a capital G and lowercase rest too.
    const apiKey = env.GEMINI_API_KEY || env.Gemini_API_KEY;
    if (!apiKey) return json({ error: "Not set up yet." }, 500);

    const model = env.GEMINI_MODEL || "gemini-3.5-flash-lite"; // change in the Worker's settings if Google retires this name
    // Give Gemini 20 seconds at most (the apps give up after 25), so a slow or overloaded Gemini fails cleanly instead of hanging.
    let res;
    try {
      res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
        method: "POST",
        signal: AbortSignal.timeout(20000),
        headers: { "content-type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM }] },
          contents: [{ role: "user", parts: [{ text: prompt }] }],
          generationConfig: { maxOutputTokens: 2000, temperature: 0.3 },
        }),
      });
    } catch (e) {
      return json({ error: "AI timed out." }, 504);
    }
    if (!res.ok) {
      // Pass Google's own short reason along (it never contains the key) so a wrong model name, a bad key or a used-up quota is easy to spot.
      let detail = "";
      try { detail = String((await res.json())?.error?.message ?? "").slice(0, 300); } catch { /* not JSON */ }
      return json({ error: "AI unavailable.", status: res.status, detail }, 502);
    }
    const data = await res.json();
    const text = (data?.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("").trim();
    if (!text) return json({ error: "No answer." }, 502);
    return json({ text });
  },
};
