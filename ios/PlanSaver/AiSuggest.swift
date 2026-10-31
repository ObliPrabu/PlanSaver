import Foundation

/// The AI half of Search. After the normal search finds plans, the app sends the search and the cheapest plans it found to a
/// small Cloudflare Worker, which asks Google's Gemini to say which to look at first. The Worker holds the Gemini key; this app
/// never does (no key may ever be in the app). Gemini is only told to choose from the plans the app already found, so it
/// cannot add plans or prices. If the call fails or is slow, nothing is shown and the normal results stand on their own.
/// Mirrors Android's AiSuggest.kt.
let aiURL = "https://plansaver-ai.plansaver-app.workers.dev"
let aiMaxPlans = 12

/// The JSON the Worker expects, or nil when there is nothing to ask about. It carries the whole search exactly as it was made
/// (lines, data, bill, big-carrier plans, free phone, other phones, each line's data, trade-in) plus the cheapest plans found.
/// Only plans for the picked phone are sent (deals about other phones are left out unless "better phones" was on), and a
/// current bill of 0 means "not entered", so it is left out too.
func buildAiRequest(_ req: SearchRequest, tradeInPhone: String?, results: [SearchResult]) -> Data? {
    let picked = results.filter { !$0.otherPhone }.sorted { $0.monthly < $1.monthly }.prefix(aiMaxPlans)
    if picked.isEmpty { return nil }
    let plans: [[String: Any]] = picked.map {
        ["carrier": $0.plan.carrier, "name": $0.plan.name, "monthly": $0.monthly,
         "dataGb": $0.plan.dataGb as Any? ?? NSNull(), "deal": $0.plan.deal as Any? ?? NSNull()]
    }
    var body: [String: Any] = ["lines": req.lines, "dataNeedGb": req.dataNeedGb as Any? ?? NSNull(), "plans": plans,
                               "includePostpaid": req.includePostpaid, "freePhoneOnly": req.freePhoneOnly,
                               "includeOtherPhones": req.includeOtherPhones]
    if req.currentMonthly > 0 { body["currentMonthly"] = req.currentMonthly }
    if let phone = req.phone, !phone.isEmpty { body["phone"] = phone }
    if let needs = req.lineDataNeeds { body["lineDataNeeds"] = needs.map { $0 as Any? ?? NSNull() } }
    if let trade = tradeInPhone, !trade.isEmpty { body["tradeInPhone"] = trade }
    return try? JSONSerialization.data(withJSONObject: body)
}

/// The suggestion text from the Worker's `{"text": "..."}` reply, or nil for an error reply or anything unreadable.
func parseAiText(_ data: Data) -> String? {
    guard let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
          let text = (obj["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else { return nil }
    return text
}

/// Asks the Worker; nil on any failure (offline, busy, not set up), so the caller simply shows nothing. Calls back on the main thread.
func fetchAiSuggestion(_ body: Data, completion: @escaping (String?) -> Void) {
    guard let url = URL(string: aiURL) else { completion(nil); return }
    var req = URLRequest(url: url, timeoutInterval: 25)
    req.httpMethod = "POST"
    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
    req.httpBody = body
    URLSession.shared.dataTask(with: req) { data, response, _ in
        let text = ((response as? HTTPURLResponse)?.statusCode == 200) ? data.flatMap(parseAiText) : nil
        DispatchQueue.main.async { completion(text) }
    }.resume()
}
