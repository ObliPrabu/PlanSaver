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
}

private let phoneMention = try! NSRegularExpression(
    pattern: "\\b(iphone\\s?\\d{1,2}(?:\\s?(?:pro max|pro|plus|mini))?|galaxy\\s?(?:s\\d{2}(?:\\+|\\s?ultra|\\s?plus)?|z\\s?(?:flip|fold)\\s?\\d*)|pixel\\s?\\d{1,2}(?:\\s?pro(?:\\s?xl)?)?)",
    options: [.caseInsensitive])

private func norm(_ s: String) -> String {
    String(s.lowercased().filter { $0.isLetter || $0.isNumber || $0 == "+" })
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
    return mentions.contains { $0.hasPrefix(want) || want.hasPrefix($0) }
}

private let brandAndGen = try! NSRegularExpression(pattern: "^(iphone|galaxys|galaxyz(?:flip|fold)|pixel)(\\d*)")

/// (brand, generation) of a normalized phone name; generation 0 when the name has no number.
private func brandGen(_ n: String) -> (String, Int)? {
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
