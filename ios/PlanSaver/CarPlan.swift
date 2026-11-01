import Foundation

/// The "My cars" questionnaire on the Cars tab: the owner types their own numbers for two vehicles and the app compares
/// them side by side. Nothing here is looked up or guessed - every figure is exactly what was entered, so there is no
/// source to cite. Mirrors Android's CarPlan.kt 1:1; keep the two in sync (tests: CarPlanTest.kt and
/// ios/LogicTests/main.swift).
///
/// Every field is kept as text so a half-filled form never loses what was typed; the functions below turn the text into
/// numbers and return nil when a figure was left blank or is not a number.
struct CarPlanEntry {
    var vehicle = ""      // make, model and year
    var price = ""        // cost of the vehicle
    var payHow = 0        // index into payOptions
    var mpg = ""
    var liability = ""    // liability insurance, paid as insFreq says
    var full = ""         // full coverage, paid as insFreq says
    var insFreq = 0       // index into insuranceFrequencies
    var oilCost = ""      // one oil change
    var oilMonths = ""    // months between oil changes
    var tires = ""        // a set of four
    var tireYears = ""    // optional: years one set lasts
    var fuelYear = ""     // fuel cost for a year
    var keepYears = ""    // estimated life of the car / how long it will be kept
    var why = ""
    var safety = ""
}

let payOptions = ["Cash", "Loan", "Lease"]
let insuranceFrequencies = ["Monthly", "Every 6 months", "Yearly"]
private let periodsPerYear: [Double] = [12, 2, 1]

let notEntered = "not entered"

/// "$1,200" or "1200.50" -> number; blank, negative or non-numeric text -> nil.
func parseAmount(_ text: String) -> Double? {
    let cleaned = text.replacingOccurrences(of: "$", with: "").replacingOccurrences(of: ",", with: "")
        .trimmingCharacters(in: .whitespaces)
    guard let v = Double(cleaned), v >= 0 else { return nil }
    return v
}

/// What an insurance payment adds up to over a year, given how often it is paid.
func insuranceYearly(_ amount: String, _ freq: Int) -> Double? {
    parseAmount(amount).map { $0 * (freq >= 0 && freq < periodsPerYear.count ? periodsPerYear[freq] : 12) }
}

extension CarPlanEntry {
    var liabilityPerYear: Double? { insuranceYearly(liability, insFreq) }
    var fullPerYear: Double? { insuranceYearly(full, insFreq) }

    /// Oil changes per year: the cost of one change times 12 / months between changes.
    var oilPerYear: Double? {
        guard let cost = parseAmount(oilCost), let months = parseAmount(oilMonths), months > 0 else { return nil }
        return cost * 12 / months
    }

    var keepYearsOrNil: Double? { parseAmount(keepYears).flatMap { $0 > 0 ? $0 : nil } }

    /// Tires over the years the car is kept. With the optional "years a set lasts" and the years kept, that is as many sets
    /// as it takes (rounded up, at least one); otherwise one set, because the app does not guess how long tires last.
    var tiresOverKeep: Double? {
        guard let cost = parseAmount(tires) else { return nil }
        var sets = 1.0
        if let setYears = parseAmount(tireYears), setYears > 0, let keep = keepYearsOrNil { sets = max(1, (keep / setYears - 1e-9).rounded(.up)) }
        return cost * sets
    }

    /// Yearly running cost with full coverage: the insurance, oil, tires (spread over the years kept) and fuel that were entered.
    var runningPerYear: Double? {
        var parts: [Double] = []
        if let v = fullPerYear { parts.append(v) }
        if let v = oilPerYear { parts.append(v) }
        if let v = parseAmount(fuelYear) { parts.append(v) }
        if let t = tiresOverKeep, let keep = keepYearsOrNil { parts.append(t / keep) }
        return parts.isEmpty ? nil : parts.reduce(0, +)
    }

    /// Price plus everything the car costs to run for the years it is kept; needs the price and the years.
    var totalOverKeep: Double? {
        guard let keep = keepYearsOrNil, let p = parseAmount(price) else { return nil }
        let yearly = [fullPerYear, oilPerYear, parseAmount(fuelYear)].compactMap { $0 }.reduce(0, +)
        return p + yearly * keep + (tiresOverKeep ?? 0)
    }

    var costPerYearOfOwning: Double? {
        guard let total = totalOverKeep, let keep = keepYearsOrNil else { return nil }
        return total / keep
    }
}

/// Always US-style digits ("1,200") and halves round up (24.5 -> 25), whatever the device's region is, so both platforms
/// print the same text.
func usFormatter(maxFraction: Int, grouping: Bool) -> NumberFormatter {
    let f = NumberFormatter()
    f.locale = Locale(identifier: "en_US")
    f.numberStyle = .decimal
    f.roundingMode = .halfUp
    f.usesGroupingSeparator = grouping
    f.minimumFractionDigits = 0
    f.maximumFractionDigits = maxFraction
    return f
}

/// "$1,200" (whole dollars, half up); nil -> "not entered".
func amount(_ v: Double?) -> String {
    guard let v = v else { return notEntered }
    return "$" + (usFormatter(maxFraction: 0, grouping: true).string(from: NSNumber(value: v)) ?? String(Int(v)))
}

/// Keeps what a number field may hold: ',' becomes '.', only ASCII 0-9 and at most one '.', at most maxLen characters.
func cleanNumberInput(_ text: String, maxLen: Int) -> String {
    var out = ""
    var seenDot = false
    for ch in text.replacingOccurrences(of: ",", with: ".") {
        if ch == "." {
            if seenDot { continue }
            seenDot = true
            out.append(ch)
        } else if ch >= "0" && ch <= "9" {
            out.append(ch)
        }
        if out.count >= maxLen { break }
    }
    return out
}

private func text(_ s: String) -> String {
    let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
    return t.isEmpty ? notEntered : t
}

/// MPG and years: no decimals for a whole number (25), one decimal when there is a fractional part (24.5).
func whole(_ v: Double?, suffix: String = "") -> String {
    guard let v = v else { return notEntered }
    let digits = v == v.rounded() ? 0 : 1
    return (usFormatter(maxFraction: digits, grouping: false).string(from: NSNumber(value: v)) ?? String(v)) + suffix
}

/// Which cost parts a car entered: insurance, oil, fuel, tires. Aggregate rows only crown a winner when both match.
func costComponents(_ e: CarPlanEntry) -> [Bool] {
    [e.fullPerYear != nil, e.oilPerYear != nil, parseAmount(e.fuelYear) != nil, e.tiresOverKeep != nil]
}

/// Side-by-side rows for the two vehicles; a row only marks a better side when both numbers were entered and differ.
func compareMyCars(_ a: CarPlanEntry, _ b: CarPlanEntry) -> [CompareRow] {
    func pay(_ e: CarPlanEntry) -> String { payOptions[min(max(e.payHow, 0), 2)] }
    let sameParts = costComponents(a) == costComponents(b)
    // A car that left parts blank would look cheaper, so with different parts entered there is no tick and values say so.
    func agg(_ x: Double?, _ y: Double?) -> (String, String, Int?) {
        if sameParts { return (amount(x), amount(y), winner(x, y, lowerIsBetter: true)) }
        func t(_ v: Double?) -> String { v == nil ? notEntered : amount(v) + " (partial)" }
        return (t(x), t(y), nil)
    }
    let run = agg(a.runningPerYear, b.runningPerYear)
    let tot = agg(a.totalOverKeep, b.totalOverKeep)
    let per = agg(a.costPerYearOfOwning, b.costPerYearOfOwning)
    return [
        CompareRow(label: "Vehicle", left: text(a.vehicle), right: text(b.vehicle), better: nil),
        CompareRow(label: "Cost of the vehicle", left: amount(parseAmount(a.price)), right: amount(parseAmount(b.price)),
                   better: winner(parseAmount(a.price), parseAmount(b.price), lowerIsBetter: true)),
        CompareRow(label: "Paying with", left: pay(a), right: pay(b), better: nil),
        CompareRow(label: "Miles per gallon", left: whole(parseAmount(a.mpg), suffix: " MPG"), right: whole(parseAmount(b.mpg), suffix: " MPG"),
                   better: winner(parseAmount(a.mpg), parseAmount(b.mpg), lowerIsBetter: false)),
        CompareRow(label: "Liability insurance per year", left: amount(a.liabilityPerYear), right: amount(b.liabilityPerYear),
                   better: winner(a.liabilityPerYear, b.liabilityPerYear, lowerIsBetter: true)),
        CompareRow(label: "Full coverage per year", left: amount(a.fullPerYear), right: amount(b.fullPerYear),
                   better: winner(a.fullPerYear, b.fullPerYear, lowerIsBetter: true)),
        CompareRow(label: "Oil changes per year", left: amount(a.oilPerYear), right: amount(b.oilPerYear),
                   better: winner(a.oilPerYear, b.oilPerYear, lowerIsBetter: true)),
        CompareRow(label: "A set of four tires", left: amount(parseAmount(a.tires)), right: amount(parseAmount(b.tires)),
                   better: winner(parseAmount(a.tires), parseAmount(b.tires), lowerIsBetter: true)),
        CompareRow(label: "Fuel per year", left: amount(parseAmount(a.fuelYear)), right: amount(parseAmount(b.fuelYear)),
                   better: winner(parseAmount(a.fuelYear), parseAmount(b.fuelYear), lowerIsBetter: true)),
        CompareRow(label: "Years you plan to keep it", left: whole(a.keepYearsOrNil), right: whole(b.keepYearsOrNil), better: nil),
        CompareRow(label: "Yearly running cost (full coverage)", left: run.0, right: run.1, better: run.2),
        CompareRow(label: "Total cost over the years you keep it (full coverage)", left: tot.0, right: tot.1, better: tot.2),
        CompareRow(label: "Cost per year of owning it (full coverage)", left: per.0, right: per.1, better: per.2),
        CompareRow(label: "Why you picked it", left: text(a.why), right: text(b.why), better: nil),
        CompareRow(label: "Safety features", left: text(a.safety), right: text(b.safety), better: nil),
    ]
}
