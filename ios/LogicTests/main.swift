// Logic tests for the iOS app's shared code (Plan.swift, PlanData.swift, PlanRepository.swift, Phones.swift).
// Compiled for the Mac itself - no simulator. Mirrors the Android unit tests; run with ios/run-logic-tests.sh.
import Foundation

var failures = 0
var count = 0

func check(_ cond: Bool, _ name: String) {
    count += 1
    if !cond { failures += 1; print("FAIL: \(name)") }
}

func plan(_ carrier: String, _ name: String = "p", network: String = "X", prepaid: Bool = true, gb: Int? = nil,
          price: Double = 30, deal: String? = nil) -> Plan {
    Plan(carrier: carrier, name: name, network: network, prepaid: prepaid, dataGb: gb, pricePerLine: [1: price],
         note: "", signupUrl: "", deal: deal)
}
func result(_ p: Plan) -> SearchResult { SearchResult(plan: p, monthly: p.perLine(1), yearlySavings: 0) }

// Phone matching
check(!dealMatchesPhone("$800 off iPhone 16 Pro", phone: "iPhone 16"), "base does not match Pro deal")
check(dealMatchesPhone("$800 off iPhone 16 Pro", phone: "iPhone 16 Pro"), "exact match")
check(dealMatchesPhone("Free iPhone 16 with trade-in", phone: "iPhone 16 Pro"), "bare family covers Pro")
check(!dealMatchesPhone("Galaxy S25 FE for $0", phone: "Galaxy S25"), "FE is separate")
check(dealMatchesPhone("Galaxy S25 Plus deal", phone: "Galaxy S25+"), "plus equals +")
check(dealMatchesPhone("Free Galaxy Z Flip 7", phone: "Galaxy Z Flip"), "foldable any generation")
check(dealMatchesPhone("Save $800 when you switch", phone: "iPhone 16"), "no phone named applies")
check(dealIsBetterPhone("$1,000 off iPhone 18 Pro", phone: "iPhone 16"), "newer iPhone is better")
check(!dealIsBetterPhone("$100 off iPhone 14", phone: "iPhone 16"), "older iPhone is not")
check(coverageLinks("Verizon, AT&T or T-Mobile").count == 3, "coverage links")
check(dealMatchesPhone("Get a free Pixel 9a when you switch", phone: "Pixel 9a"), "Pixel 9a matches itself")
check(!dealMatchesPhone("Get a free Pixel 9a when you switch", phone: "Pixel 9"), "Pixel 9a deal hidden from Pixel 9")
check(dealMatchesPhone("Save on Pixel 9 when you switch", phone: "Pixel 9a"), "bare Pixel 9 covers 9a")
check(!dealMatchesPhone("Free iPhone XR with trade-in", phone: "iPhone 16"), "XR deal hidden from 16")
check(dealMatchesPhone("Free iPhone XR with trade-in", phone: "iPhone XR"), "XR matches itself")

// Phone lists
let iphones = Phones.brands["iPhone"]!.map { $0.label }
check(iphones.first == "iPhone XS" && iphones.last == "iPhone Duo", "iPhone range")
check(Phones.brands["Samsung"]!.first!.label == "Galaxy S20" && Phones.brands["Samsung"]!.contains { $0.label == "Galaxy S26" }, "Samsung range")
check(Phones.brands["Google Pixel"]!.map { $0.label } == ["Pixel 4", "Pixel 5", "Pixel 6", "Pixel 7", "Pixel 8", "Pixel 9", "Pixel 10"], "Pixel range")
check(Phones.brands["iPhone"]![Phones.defaultIphoneIdx].label == "iPhone 16", "default is iPhone 16")

// Quiz
check(quizDataIndex(usage: 0, homeWifi: true, hotspot: false) == 0, "quiz low")
check(quizDataIndex(usage: 1, homeWifi: false, hotspot: false) == 2, "quiz no wifi")
check(quizDataIndex(usage: 2, homeWifi: false, hotspot: true) == 3, "quiz capped at unlimited")

// Total cost
let c = totalCost(monthly: 30, months: 24, phonePrice: 800, tradeIn: 200)
check(c.plan == 720 && c.phone == 600 && c.total == 1320, "trade-in comes off the phone")
check(totalCost(monthly: 30, months: 24, phonePrice: 800, tradeIn: 200, activation: 70).total == 1390, "activation added")
check(totalCost(monthly: 30, months: 12, phonePrice: 100, tradeIn: 500).phone == 0, "phone never negative")
check(totalCost(monthly: 30, months: 12, phonePrice: 0, tradeIn: 500).total == 360, "trade-in needs a phone price")

// Top picks
let list = [result(plan("A", "Cheap", price: 20)), result(plan("B", "Mid", price: 40)), result(plan("C", "Small", gb: 5, price: 15))]
let picks = topPicks(list)
check(picks.count == 3 && picks[0].result.plan.carrier == "C" && picks[1].result.plan.carrier == "A" && picks[2].result.plan.carrier == "B", "top picks order")
let single = topPicks([result(plan("A", "Only", price: 20))])
check(single.count == 1 && single[0].label == "Cheapest + Cheapest unlimited", "single unlimited plan has no Premium label")

// Filters
let all = [result(plan("Visible", network: "Verizon", deal: "deal")), result(plan("Cricket", network: "AT&T", gb: 10)),
           result(plan("T-Mobile", network: "T-Mobile", prepaid: false)), result(plan("US Mobile", network: "Choice of networks"))]
check(applyFilters(all, ResultFilters()).count == 4, "no filters keeps everything")
check(applyFilters(all, ResultFilters(networks: ["Verizon", "AT&T"])).count == 2, "networks OR together")
check(applyFilters(all, ResultFilters(networks: ["Verizon", "AT&T"], unlimitedOnly: true)).count == 1, "filters AND together")
check(applyFilters(all, ResultFilters(type: 2)).count == 1 && applyFilters(all, ResultFilters(type: 1)).count == 3, "prepaid/postpaid")
check(applyFilters(all, ResultFilters(dealOnly: true)).first?.plan.carrier == "Visible", "deal only")

// Search (bundled plans, no network)
let mixed = PlanRepository.bundledSearch(SearchRequest(lines: 2, dataNeedGb: nil, currentMonthly: 0, includePostpaid: false, lineDataNeeds: [5, nil]))
let mint = mixed.first { $0.plan.carrier == "Mint Mobile" }
check(mint?.assignments?.map { $0.plan.name } == ["6GB (12-month)", "Unlimited (12-month)"], "each line gets its own cheapest plan")
check(mint?.monthly == 45, "mixed total is the sum")
let normal = PlanRepository.bundledSearch(SearchRequest(lines: 1, dataNeedGb: nil, currentMonthly: 0, includePostpaid: true))
check(normal.first?.assignments == nil && !normal.isEmpty, "normal search has no assignments")
check(PlanData.plans.count == 45, "45 plans bundled")

print("\(count - failures) of \(count) checks passed" + (failures == 0 ? "" : ", \(failures) FAILED"))
exit(failures == 0 ? 0 : 1)
