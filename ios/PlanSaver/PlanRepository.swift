import Foundation

/// Hosted deals.json (refreshed by the repo's GitHub Action). Needs the PlanSaver repo to be PUBLIC; if it is
/// private or offline, search quietly falls back to the bundled deals.
let dealsURL = "https://raw.githubusercontent.com/ObliPrabu/PlanSaver/main/deals-data/deals.json"

private struct Deal {
    let carrier: String
    let deal: String
    let freePhone: Bool
    let url: String
    /// Plan-name fragments this deal is for (e.g. "Experience", "6-month"); empty = every plan of the carrier.
    let plans: [String]
    let updated: String
    let linesNote: String
    let linesUpdated: String
    let extras: [String]
    let activationFee: Double?
    let tradeInMax: Double?

    func coversPlan(_ name: String) -> Bool {
        plans.isEmpty || plans.contains { name.range(of: $0, options: .caseInsensitive) != nil }
    }
}

enum PlanRepository {
    /// Filter + sort the bundled plans. Mirrors Android's BundledPlanRepository.
    static func bundledSearch(_ request: SearchRequest) -> [SearchResult] {
        if let needs = request.lineDataNeeds { return mixedSearch(request, needs: needs) }
        return PlanData.plans
            .filter { request.includePostpaid || $0.prepaid }
            // A plan only priced for N+ lines (e.g. AT&T Extra at 4 lines) must not show up for fewer lines.
            .filter { request.lines >= ($0.pricePerLine.keys.min() ?? 1) }
            .filter { plan in
                guard let have = plan.dataGb else { return true }
                guard let need = request.dataNeedGb else { return false }
                return have >= need
            }
            .map { plan in
                let m = plan.monthly(request.lines)
                return SearchResult(plan: plan, monthly: m, yearlySavings: (request.currentMonthly - m) * 12)
            }
            .sorted { $0.monthly < $1.monthly }
    }

    /// Customized lines: for each carrier, every line gets the cheapest of that carrier's plans that covers the line's own
    /// data need. The carrier's total is the sum (per-line prices still depend on the total line count). Carriers that
    /// cannot cover every line are left out. Whether a carrier lets you mix plans on one account is not checked.
    private static func mixedSearch(_ request: SearchRequest, needs: [Int?]) -> [SearchResult] {
        let eligible = PlanData.plans
            .filter { request.includePostpaid || $0.prepaid }
            .filter { request.lines >= ($0.pricePerLine.keys.min() ?? 1) }
        return Dictionary(grouping: eligible, by: { $0.carrier }).values.compactMap { plans -> SearchResult? in
            var picks: [LineAssignment] = []
            for (i, need) in needs.prefix(request.lines).enumerated() {
                let fits = plans.filter { p in
                    guard let have = p.dataGb else { return true }
                    guard let n = need else { return false }
                    return have >= n
                }
                guard let best = fits.min(by: { $0.perLine(request.lines) < $1.perLine(request.lines) }) else { return nil }
                picks.append(LineAssignment(line: i + 1, plan: best, price: best.perLine(request.lines)))
            }
            let total = picks.reduce(0) { $0 + $1.price }
            guard let primary = picks.max(by: { $0.price < $1.price })?.plan else { return nil }
            var r = SearchResult(plan: primary, monthly: total, yearlySavings: (request.currentMonthly - total) * 12)
            r.assignments = picks
            return r
        }.sorted { $0.monthly < $1.monthly }
    }

    /// Runs on every Search tap: bundled plans merged with the freshly downloaded deals.
    static func search(_ request: SearchRequest, completion: @escaping ([SearchResult]) -> Void) {
        var bundledRequest = request
        bundledRequest.freePhoneOnly = false
        let base = bundledSearch(bundledRequest)

        fetchDeals(phone: request.phone) { live in
            let merged: [SearchResult] = base.map { r in
                let carrierDeals = (live ?? []).filter { sameCarrier($0.carrier, r.plan.carrier) }
                var out = r
                // The multi-line note is about the carrier, not one deal, so any of its entries may supply it.
                if let n = carrierDeals.first(where: { !$0.linesNote.isEmpty }) {
                    out.plan.linesNote = n.linesNote
                    out.plan.linesDate = n.linesUpdated
                }
                if let e = carrierDeals.first(where: { !$0.extras.isEmpty }) { out.plan.extraNotes = e.extras }
                out.plan.activationFee = carrierDeals.compactMap { $0.activationFee }.first
                out.plan.tradeInMax = carrierDeals.compactMap { $0.tradeInMax }.first
                guard let d = carrierDeals.first(where: { $0.coversPlan(r.plan.name) }) else { return out }
                out.plan.deal = d.deal
                out.plan.freePhoneOffer = d.freePhone
                out.plan.dealUrl = d.url
                out.plan.dealDate = d.updated
                out.liveDeal = true
                return out
            }
            // Deals about your phone (or no phone) stay. Deals about another phone are hidden, unless "better phones"
            // is ticked and the phone is a newer one of the same brand - those are kept, labeled and listed last.
            let mapped: [SearchResult] = merged.map { r in
                guard let text = r.plan.deal, !dealMatchesPhone(text, phone: request.phone) else { return r }
                var out = r
                if request.includeOtherPhones && dealIsBetterPhone(text, phone: request.phone) {
                    out.otherPhone = true
                } else {
                    out.plan.deal = nil
                    out.plan.freePhoneOffer = false
                    out.plan.dealUrl = ""
                    out.plan.dealDate = ""
                    out.liveDeal = false
                }
                return out
            }
            // Stable partition: your-phone results first, better-phone results after, price order kept in each.
            let relevant = mapped.filter { !$0.otherPhone } + mapped.filter { $0.otherPhone }
            let result: [SearchResult]
            if !request.freePhoneOnly {
                result = relevant
            } else {
                // With downloaded data a carrier with no offer is dropped; without it keep the bundled flags.
                result = relevant.filter { $0.plan.freePhoneOffer && (live == nil || $0.liveDeal) }
            }
            DispatchQueue.main.async { completion(result) }
        }
    }

    /// "Hotspot: text (as of date)" for each extra the robot found.
    private static func extraNotes(_ o: [String: Any]?) -> [String] {
        let labels = ["hotspot": "Hotspot", "international": "International", "perks": "Perks", "slowdown": "Slowdown"]
        return (o ?? [:]).keys.sorted().compactMap { k in
            guard let n = o?[k] as? [String: Any], let text = n["text"] as? String, !text.isEmpty else { return nil }
            let date = n["updated"] as? String ?? ""
            return "\(labels[k] ?? k): \(text)" + (date.isEmpty ? "" : " (as of \(date))")
        }
    }

    private static func sameCarrier(_ a: String, _ b: String) -> Bool {
        func norm(_ s: String) -> String {
            s.lowercased().replacingOccurrences(of: " mobile", with: "")
                .replacingOccurrences(of: "by t-mobile", with: "")
                .trimmingCharacters(in: .whitespaces)
        }
        let x = norm(a), y = norm(b)
        return x == y || x.hasPrefix(y) || y.hasPrefix(x)
    }

    /// Deals that apply to `phone`: entries with an empty "phones" list apply to every phone.
    private static func fetchDeals(phone: String?, completion: @escaping ([Deal]?) -> Void) {
        guard let url = URL(string: dealsURL) else { return completion(nil) }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.httpMethod = "GET"
        URLSession.shared.dataTask(with: req) { data, response, _ in
            guard let http = response as? HTTPURLResponse, http.statusCode == 200, let data = data,
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let arr = root["deals"] as? [[String: Any]] else { return completion(nil) }
            let deals: [Deal] = arr.compactMap { o in
                guard let carrier = o["carrier"] as? String, let deal = o["deal"] as? String else { return nil }
                let phones = o["phones"] as? [String] ?? []
                let applies = phones.isEmpty || phone == nil || phones.contains { $0.caseInsensitiveCompare(phone!) == .orderedSame }
                guard applies else { return nil }
                return Deal(carrier: carrier, deal: deal, freePhone: o["freePhone"] as? Bool ?? false,
                            url: o["url"] as? String ?? "", plans: o["plans"] as? [String] ?? [],
                            updated: o["updated"] as? String ?? "",
                            linesNote: o["lines"] as? String ?? "", linesUpdated: o["linesUpdated"] as? String ?? "",
                            extras: extraNotes(o["extras"] as? [String: Any]),
                            activationFee: o["activationFee"] as? Double, tradeInMax: o["tradeInMax"] as? Double)
            }
            completion(deals)
        }.resume()
    }
}
