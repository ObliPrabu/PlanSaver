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
}

struct SearchResult: Identifiable {
    let id = UUID()
    var plan: Plan
    let monthly: Double
    let yearlySavings: Double
    /// The deal text came from the downloaded deals.json rather than the bundled snapshot.
    var liveDeal = false
}
