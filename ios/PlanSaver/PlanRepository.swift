import Foundation

/// Hosted deals.json (refreshed by the repo's GitHub Action). Needs the PlanSaver repo to be PUBLIC; if it is
/// private or offline, search quietly falls back to the bundled deals.
let dealsURL = "https://raw.githubusercontent.com/ObliPrabu/PlanSaver/main/deals-data/deals.json"

struct LiveDeal {
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
    /// Hand-set rule the robot never edits (the carrier's own terms, e.g. AT&T's trade-in credit needing Premium or Elite).
    var requiresPlans: [String] = []

    /// `plans` is the robot-managed scope of the deal text; `requiresPlans` is the carrier's own rule. The deal applies only to plans passing both.
    func coversPlan(_ name: String) -> Bool { nameMatches(name, plans) && nameMatches(name, requiresPlans) }
}

/// True when `fragments` is empty (no restriction) or the plan name contains one of them, ignoring case.
func nameMatches(_ name: String, _ fragments: [String]) -> Bool {
    fragments.isEmpty || fragments.contains { name.range(of: $0, options: .caseInsensitive) != nil }
}

enum PlanRepository {
    /// Filter + sort the bundled plans. Mirrors Android's BundledPlanRepository.
    /// Only plans that `allow` says yes to are considered (used to keep a free-phone search on the plans its deal covers).
    static func bundledSearch(_ request: SearchRequest, allow: (Plan) -> Bool = { _ in true }) -> [SearchResult] {
        if let needs = request.lineDataNeeds { return mixedSearch(request, needs: needs, allow: allow) }
        return PlanData.plans
            .filter(allow)
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
    private static func mixedSearch(_ request: SearchRequest, needs: [Int?], allow: (Plan) -> Bool) -> [SearchResult] {
        let eligible = PlanData.plans
            .filter(allow)
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
        }.sorted { $0.monthly == $1.monthly ? $0.plan.carrier < $1.plan.carrier : $0.monthly < $1.monthly } // equal totals: by carrier name, so the order never changes between searches
    }

    /// Runs on every Search tap: bundled plans merged with the freshly downloaded deals.
    static func search(_ request: SearchRequest, completion: @escaping ([SearchResult]) -> Void) {
        var bundledRequest = request
        bundledRequest.freePhoneOnly = false

        fetchDeals(phone: request.phone) { live in
            // A free-phone search only considers the plans a carrier's free-phone deal covers (e.g. AT&T's trade-in credit needs
            // Premium or Elite), so with custom lines it picks a qualifying plan instead of the cheapest one that gets no deal.
            let base = bundledSearch(bundledRequest) { p in
                !request.freePhoneOnly || live == nil || (live ?? []).contains { sameCarrierName($0.carrier, p.carrier) && $0.freePhone && $0.coversPlan(p.name) }
            }
            let merged = mergeLiveDeals(base, live)
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

    /// Deals that apply to `phone`: entries with an empty "phones" list apply to every phone.
    private static func fetchDeals(phone: String?, completion: @escaping ([LiveDeal]?) -> Void) {
        guard let url = URL(string: dealsURL) else { return completion(nil) }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.httpMethod = "GET"
        URLSession.shared.dataTask(with: req) { data, response, _ in
            guard let http = response as? HTTPURLResponse, http.statusCode == 200, let data = data,
                  let deals = parseLiveDeals(data, phone: phone, today: todayISO()) else { return completion(nil) }
            completion(deals)
        }.resume()
    }
}

/// Lays the live deals over the searched plans. When `live` is nil (offline / bad URL) nothing is enforced beyond the bundled
/// data: bundled deal text and flags stay as they are, because there is nothing newer to judge them against. When live deals
/// exist for a plan's carrier but none covers the plan (e.g. AT&T Value 2.0 under requiresPlans), the plan's bundled deal,
/// free-phone flag and link are cleared so a stale "phone deal" is not shown.
func mergeLiveDeals(_ base: [SearchResult], _ live: [LiveDeal]?) -> [SearchResult] {
    base.map { r in
        let carrierDeals = (live ?? []).filter { sameCarrierName($0.carrier, r.plan.carrier) }
        var out = r
        // The multi-line note is about the carrier, not one deal, so any of its entries may supply it.
        if let n = carrierDeals.first(where: { !$0.linesNote.isEmpty }) {
            out.plan.linesNote = n.linesNote
            out.plan.linesDate = n.linesUpdated
        }
        if let e = carrierDeals.first(where: { !$0.extras.isEmpty }) { out.plan.extraNotes = e.extras }
        // Fees are about the carrier, like the multi-line and extras notes, so they ignore the per-plan `plans` scope.
        out.plan.activationFee = carrierDeals.compactMap { $0.activationFee }.first
        out.plan.tradeInMax = carrierDeals.compactMap { $0.tradeInMax }.first
        guard let d = carrierDeals.first(where: { $0.coversPlan(r.plan.name) }) else {
            if !carrierDeals.isEmpty {
                out.plan.deal = nil
                out.plan.freePhoneOffer = false
                out.plan.dealUrl = ""
                out.plan.dealDate = ""
            }
            return out
        }
        out.plan.deal = d.deal
        out.plan.freePhoneOffer = d.freePhone
        out.plan.dealUrl = d.url
        out.plan.dealDate = d.updated
        out.liveDeal = true
        return out
    }
}

func sameCarrierName(_ a: String, _ b: String) -> Bool {
    func norm(_ s: String) -> String {
        s.lowercased().replacingOccurrences(of: " mobile", with: "")
            .replacingOccurrences(of: "by t-mobile", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
    let x = norm(a), y = norm(b)
    return x == y || x.hasPrefix(y) || y.hasPrefix(x)
}

/// True when a hand-set `expires` date (YYYY-MM-DD) is before `today` (same format, so plain string order is date order). Blank = never expires.
func dealExpired(_ expires: String, today: String) -> Bool { !expires.isEmpty && expires < today }

/// Reads deals.json bytes. Tolerant per entry (like Android): an entry missing carrier/deal, about another phone, or past its
/// hand-set `expires` date is skipped on its own. Hand-set keys the robot never edits: `requiresPlans`, `expires`, and
/// `ignoreFreePhone` (treat the entry as not a free-phone offer even though the robot's text check set freePhone).
func parseLiveDeals(_ data: Data, phone: String?, today: String) -> [LiveDeal]? {
    guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let arr = root["deals"] as? [[String: Any]] else { return nil }
    return arr.compactMap { o in
        guard let carrier = o["carrier"] as? String, let deal = o["deal"] as? String else { return nil }
        if dealExpired(o["expires"] as? String ?? "", today: today) { return nil }
        let phones = o["phones"] as? [String] ?? []
        let applies = phones.isEmpty || phone == nil || phones.contains { $0.caseInsensitiveCompare(phone!) == .orderedSame }
        guard applies else { return nil }
        return LiveDeal(carrier: carrier, deal: deal,
                        freePhone: (o["freePhone"] as? Bool ?? false) && !(o["ignoreFreePhone"] as? Bool ?? false),
                        url: o["url"] as? String ?? "", plans: o["plans"] as? [String] ?? [],
                        updated: o["updated"] as? String ?? "",
                        linesNote: o["lines"] as? String ?? "", linesUpdated: o["linesUpdated"] as? String ?? "",
                        extras: dealExtraNotes(o["extras"] as? [String: Any]),
                        activationFee: o["activationFee"] as? Double, tradeInMax: o["tradeInMax"] as? Double,
                        requiresPlans: o["requiresPlans"] as? [String] ?? [])
    }
}

/// "Hotspot: text (as of date)" for each extra the robot found, sorted by key (the same order on Android).
func dealExtraNotes(_ o: [String: Any]?) -> [String] {
    let labels = ["hotspot": "Hotspot", "international": "International", "perks": "Perks", "slowdown": "Slowdown",
                  "activation": "Activation", "tradein": "Trade-in"]
    return (o ?? [:]).keys.sorted().compactMap { k in
        guard let n = o?[k] as? [String: Any], let text = n["text"] as? String, !text.isEmpty else { return nil }
        let date = n["updated"] as? String ?? ""
        return "\(labels[k] ?? k): \(text)" + (date.isEmpty ? "" : " (as of \(date))")
    }
}

private func todayISO() -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: Date())
}
