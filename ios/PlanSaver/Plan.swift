import Foundation

/// One carrier plan. Prices are USD/month per line, as researched in Oct 2026.
struct Plan {
    let carrier: String
    let name: String
    let network: String
    let prepaid: Bool
    /// nil = unlimited high-speed data.
    let dataGb: Int?
    /// Per-line price when buying this many lines; other counts fall back to the nearest known one.
    let pricePerLine: [Int: Double]
    var note: String = ""
    let signupUrl: String
    var perks: [String] = []
    /// Current promo text. nil when none known.
    var deal: String? = nil
    /// True when the carrier advertises a free or discounted phone for switchers.
    var freePhoneOffer: Bool = false
    /// Page the live deal came from (empty for bundled data).
    var dealUrl: String = ""
    /// Date (YYYY-MM-DD) the live deal text was last refreshed; empty for bundled data.
    var dealDate: String = ""
    /// Multi-line price sentence the robot found on the carrier's pages (unverified; shown, never used to compute prices).
    var linesNote: String = ""
    var linesDate: String = ""
    /// Hotspot / international / perks / slowdown sentences the robot found for the carrier (unverified), already labeled.
    var extraNotes: [String] = []
    /// Months paid up front (Mint sells 3/6/12-month blocks). 1 = month to month.
    var termMonths: Int = 1

    func perLine(_ lines: Int) -> Double {
        if let exact = pricePerLine[lines] { return exact }
        let known = pricePerLine.keys.sorted()
        let nearest = known.last(where: { $0 <= lines }) ?? known[0]
        return pricePerLine[nearest]!
    }

    func monthly(_ lines: Int) -> Double { perLine(lines) * Double(lines) }
}

struct SearchRequest {
    var lines: Int
    var dataNeedGb: Int? // nil = unlimited
    var currentMonthly: Double
    var includePostpaid: Bool
    var freePhoneOnly = false
    /// e.g. "iPhone 16 Pro"; only set when freePhoneOnly is on.
    var phone: String? = nil
    /// Also keep deals about a different specific phone (e.g. an iPhone 18 deal when you picked a 16).
    var includeOtherPhones = false
    /// Data need of each line (nil entry = unlimited). When set, every line gets its own cheapest plan of a carrier.
    var lineDataNeeds: [Int?]? = nil
}

/// One line's plan in a mixed-plan result.
struct LineAssignment: Identifiable {
    let line: Int
    let plan: Plan
    let price: Double
    var id: Int { line }
}

struct SearchResult: Identifiable {
    let id = UUID()
    var plan: Plan
    let monthly: Double
    let yearlySavings: Double
    /// The deal text came from the downloaded deals.json rather than the bundled snapshot.
    var liveDeal = false
    /// The deal is for a newer phone than the one picked ("check for better phones as well").
    var otherPhone = false
    /// Set when each line got its own plan (customized lines); `plan` is then the carrier's priciest pick.
    var assignments: [LineAssignment]? = nil
}

private let phoneMention = try! NSRegularExpression(
    pattern: "\\b(iphone\\s?duo|iphone\\s?air|iphone\\s?\\d{1,2}e?(?:\\s?(?:pro max|pro|plus|mini))?|galaxy\\s?(?:s\\d{2}(?:\\+|\\s?ultra\\b|\\s?plus\\b|\\s?fe\\b|\\s?edge\\b)?|z\\s?(?:flip|fold)\\s?\\d*)|pixel\\s?\\d{1,2}(?:\\s?pro(?:\\s?(?:xl|fold))?)?)",
    options: [.caseInsensitive])

private func norm(_ s: String) -> String {
    let n = String(s.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "+" })
    return n.hasPrefix("galaxy") ? n.replacingOccurrences(of: "plus", with: "+") : n // "S25 Plus" == "S25+"
}

/// True when a deal's text is about the phone the user picked, or names no specific phone at all (generic offers
/// always apply). A deal that only names other phones (e.g. "iPhone 18 Pro Max") is not about `phone`.
func dealMatchesPhone(_ text: String, phone: String?) -> Bool {
    guard let phone = phone else { return true }
    let ns = text as NSString
    let mentions = phoneMention.matches(in: text, range: NSRange(location: 0, length: ns.length))
        .map { norm(ns.substring(with: $0.range)) }
    if mentions.isEmpty { return true }
    let want = norm(phone)
    // Exact match: picking the base "iPhone 16" must not match a deal for the "iPhone 16 Pro", nor "S25" a "S25 FE".
    // Two exceptions: a bare family mention ("iPhone 16", "Pixel 9") covers the whole family, so it matches the Pro
    // you picked; and foldables can't be picked by generation, so "Galaxy Z Flip 7" matches the picker's "Galaxy Z Flip".
    return mentions.contains { $0 == want || (isBareFamily($0) && want.hasPrefix($0)) || foldableFamily($0) == want }
}

private func isBareFamily(_ n: String) -> Bool {
    n.range(of: "^(iphone\\d+|galaxys\\d+|pixel\\d+)$", options: .regularExpression) != nil
}

private func foldableFamily(_ n: String) -> String {
    guard n.hasPrefix("galaxyz") else { return n }
    var out = n
    while let last = out.last, last.isNumber { out.removeLast() }
    return out
}

private let brandAndGen = try! NSRegularExpression(pattern: "^(iphone|galaxys|galaxyz(?:flip|fold)|pixel)(\\d*)")

/// (brand, generation) of a normalized phone name; generation 0 when the name has no number.
private func brandGen(_ n: String) -> (String, Int)? {
    if n.hasPrefix("iphoneduo") { return ("iphone", 99) } // newest iPhone, newer than any numbered one
    let ns = n as NSString
    guard let m = brandAndGen.firstMatch(in: n, range: NSRange(location: 0, length: ns.length)) else { return nil }
    return (ns.substring(with: m.range(at: 1)), Int(ns.substring(with: m.range(at: 2))) ?? 0)
}

/// True when the deal names a phone of the same brand as `phone` and a generation at least as new (a "better" phone).
func dealIsBetterPhone(_ text: String, phone: String?) -> Bool {
    guard let phone = phone, let want = brandGen(norm(phone)) else { return false }
    let ns = text as NSString
    return phoneMention.matches(in: text, range: NSRange(location: 0, length: ns.length)).contains { m in
        guard let got = brandGen(norm(ns.substring(with: m.range))) else { return false }
        return got.0 == want.0 && got.1 >= want.1
    }
}

/// Official coverage-map pages for the networks a plan runs on (a plan on several networks gets several links).
func coverageLinks(_ network: String) -> [(name: String, url: String)] {
    var out: [(name: String, url: String)] = []
    if network.contains("Verizon") { out.append(("Verizon", "https://www.verizon.com/coverage-map/")) }
    if network.contains("AT&T") { out.append(("AT&T", "https://www.att.com/maps/wireless-coverage.html")) }
    if network.contains("T-Mobile") { out.append(("T-Mobile", "https://www.t-mobile.com/coverage/coverage-map")) }
    return out
}

/// "Not sure how much data?" quiz: index into the data choices (0 = 1-5 GB, 1 = 10, 2 = 25, 3 = unlimited). `usage` is
/// 0 = texting/reading, 1 = music/social, 2 = video streaming. No home Wi-Fi or sharing a hotspot each push it one step
/// up. A rough guess, not a measurement.
func quizDataIndex(usage: Int, homeWifi: Bool, hotspot: Bool) -> Int {
    min(max(usage + (homeWifi ? 0 : 1) + (hotspot ? 1 : 0), 0), 3)
}

/// A short label plus the result it points at, for the "Top picks" box.
struct TopPick: Identifiable {
    let label: String
    let result: SearchResult
    var id: UUID { result.id }
}

/// Up to three picks from the plans shown: the cheapest, the cheapest unlimited, and the priciest unlimited (postpaid
/// plans usually carry the most perks, but that is not checked). Same plan twice gets one entry with both labels.
/// Mixed-plan (customized lines) results are skipped.
func topPicks(_ list: [SearchResult]) -> [TopPick] {
    let plain = list.filter { !$0.otherPhone && $0.assignments == nil }
    let unlimited = plain.filter { $0.plan.dataGb == nil }
    let wanted: [(String, SearchResult)] = [
        plain.min(by: { $0.monthly < $1.monthly }).map { ("Cheapest", $0) },
        unlimited.min(by: { $0.monthly < $1.monthly }).map { ("Cheapest unlimited", $0) },
        unlimited.max(by: { $0.monthly < $1.monthly }).map { ("Premium (priciest unlimited)", $0) },
    ].compactMap { $0 }
    var out: [(key: String, labels: [String], result: SearchResult)] = []
    for (label, r) in wanted {
        let key = r.plan.carrier + r.plan.name
        if let i = out.firstIndex(where: { $0.key == key }) { out[i].labels.append(label) }
        else { out.append((key, [label], r)) }
    }
    return out.map { TopPick(label: $0.labels.joined(separator: " + "), result: $0.result) }
}

/// What the user pays over `months`: the plan, plus the phone paid in full minus any trade-in value (never below 0).
struct TotalCost {
    let months: Int
    let plan: Double
    let phone: Double
    let total: Double
}

func totalCost(monthly: Double, months: Int, phonePrice: Double, tradeIn: Double) -> TotalCost {
    let phone = phonePrice > 0 ? max(phonePrice - tradeIn, 0) : 0
    return TotalCost(months: months, plan: monthly * Double(months), phone: phone, total: monthly * Double(months) + phone)
}
