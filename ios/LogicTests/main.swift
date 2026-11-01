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

// Catalog (phones, cars, insurers)
let catalogJSON = """
{"phones":[
  {"id":"a","name":"Phone A","brand":"X","url":"https://x.com","price":799.0,"display":6.3,"batteryMah":4500,"chip":"A19","storage":"256GB, 512GB","updated":"2026-10-20","sources":{"price":"https://www.apple.com/a"}},
  {"id":"b","name":"Phone B","brand":"Y","url":"https://y.com","price":999.99,"batteryMah":5000}
],
"cars":[
  {"id":"c1","name":"Gas Car","make":"G","url":"https://g.com","mpgCombined":35,"mpgCity":32,"mpgHighway":40,"mpgUnit":"MPG","annualFuelCost":1650,"price":25000.0},
  {"id":"c2","name":"EV","make":"E","url":"https://e.com","mpgCombined":130,"mpgUnit":"MPGe","annualFuelCost":800,"rangeMiles":300}
],
"insurers":[
  {"id":"i1","name":"Cheap","url":"https://c.com","fullMonthly":100.0,"minMonthly":40.0,"sources":{"fullMonthly":"https://www.nerdwallet.com/p"}},
  {"id":"i2","name":"None","url":"https://n.com"},
  {"id":"i3","name":"Pricey","url":"https://p.com","fullMonthly":170.0}
]}
"""
let cat = Catalog.parse(catalogJSON.data(using: .utf8)!)!
check(cat.phones[0].price == 799 && cat.phones[1].chip == nil && cat.phones[1].display == nil, "catalog parses numbers and leaves missing ones nil")
check(cat.phones[0].sources["price"] == "https://www.apple.com/a" && cat.insurers[1].fullMonthly == nil, "catalog sources and missing insurer figure")
let pRows = Dictionary(uniqueKeysWithValues: comparePhones(cat.phones[0], cat.phones[1]).map { ($0.label, $0) })
check(pRows["Starting price"]?.better == 0 && pRows["Battery"]?.better == 1 && pRows["Chip"]?.right == "not found yet" && pRows["Display"]?.better == nil, "phone comparison marks the better side")
let cRows = Dictionary(uniqueKeysWithValues: compareCars(cat.cars[0], cat.cars[1]).map { ($0.label, $0) })
check(cRows["Fuel economy (combined)"]?.better == nil && cRows["Fuel economy (combined)"]?.right == "130 MPGe", "MPG is not compared with MPGe")
check(cRows["Fuel cost per year (EPA estimate)"]?.better == 1 && cRows["Starting price"]?.right == "not found yet", "car comparison fuel cost and missing price")
check(sortInsurers(cat.insurers, full: true).map { $0.name } == ["Cheap", "Pricey", "None"], "insurers cheapest first, missing last")
check(sortInsurers(cat.insurers, full: false).first?.name == "Cheap", "minimum coverage sort")
check(sourceDomain("https://www.nerdwallet.com/insurance/auto") == "nerdwallet.com" && sourceDomain(nil) == nil, "source domain")
let realCatalog = Catalog.parse(try! Data(contentsOf: URL(fileURLWithPath: "../deals-data/catalog.json")))!
check(realCatalog.phones.count >= 10 && realCatalog.cars.count >= 10 && realCatalog.insurers.count >= 5, "the catalog file in the repo parses and has names")

// ---- Free-phone searches stay on the plans a deal covers (mirrors MixedLinesTest.kt)
let attReq = SearchRequest(lines: 2, dataNeedGb: nil, currentMonthly: 0, includePostpaid: true, lineDataNeeds: [25, 10])
let attOpen = PlanRepository.bundledSearch(attReq).first { $0.plan.carrier == "AT&T" }
let attQual = PlanRepository.bundledSearch(attReq) { nameMatches($0.name, ["Premium", "Elite"]) }.first { $0.plan.carrier == "AT&T" }
check(attOpen?.assignments?.allSatisfy { $0.plan.name.hasPrefix("Value") } == true, "unrestricted: both lines take the cheapest AT&T plan (Value)")
check(attQual?.assignments?.allSatisfy { $0.plan.name.hasPrefix("Premium") } == true && (attQual?.monthly ?? 0) > (attOpen?.monthly ?? 0), "restricted to qualifying plans: both lines take Premium")
check(nameMatches("Premium 2.0", ["premium", "Elite"]) && !nameMatches("Value 2.0", ["Premium", "Elite"]) && nameMatches("Value 2.0", []), "plan name matching")

// ---- My cars questionnaire (mirrors CarPlanTest.kt)
let ca = CarPlanEntry(vehicle: "2022 Honda Civic", price: "$24,000", payHow: 1, mpg: "35", liability: "50", full: "100", insFreq: 0,
                      oilCost: "60", oilMonths: "6", tires: "600", tireYears: "4", fuelYear: "1,500", keepYears: "8")
let cb = CarPlanEntry(vehicle: "2020 Ford F-150", price: "30000", mpg: "20", full: "600", insFreq: 1, fuelYear: "2500", keepYears: "8")
check(parseAmount("$24,000") == 24000 && parseAmount(" 12.5 ") == 12.5 && parseAmount("") == nil && parseAmount("abc") == nil && parseAmount("-5") == nil, "car plan parses amounts")
check(ca.fullPerYear == 1200 && ca.liabilityPerYear == 600 && cb.fullPerYear == 1200 && insuranceYearly("500", 2) == 500, "insurance scaled by payment frequency")
check(ca.oilPerYear == 120 && CarPlanEntry(oilCost: "60", oilMonths: "0").oilPerYear == nil && CarPlanEntry(oilCost: "60").oilPerYear == nil, "oil changes per year")
var ct = ca
check(ct.tiresOverKeep == 1200, "tires: 8 years / 4 = 2 sets")
ct.tireYears = ""
check(ct.tiresOverKeep == 600, "tires: one set without a lifespan")
ct.tireYears = "3"
check(ct.tiresOverKeep == 1800 && CarPlanEntry().tiresOverKeep == nil, "tires: rounds sets up")
check(ca.runningPerYear == 2970 && ca.totalOverKeep == 47760 && ca.costPerYearOfOwning == 5970, "running, total and per-year cost")
check(CarPlanEntry(price: "1000").totalOverKeep == nil && CarPlanEntry().runningPerYear == nil, "totals need what they depend on")
let myRows = Dictionary(uniqueKeysWithValues: compareMyCars(ca, cb).map { ($0.label, $0) })
check(myRows["Cost of the vehicle"]?.better == 0 && myRows["Miles per gallon"]?.better == 0 && myRows["Full coverage per year"]?.better == nil, "my cars: better side and ties")
check(myRows["Oil changes per year"]?.right == "not entered" && myRows["Oil changes per year"]?.better == nil && myRows["Paying with"]?.left == "Loan" && myRows["Paying with"]?.right == "Cash", "my cars: blanks and payment")

// ---- AI search request (mirrors AiSuggestTest.kt)
func aiResult(_ carrier: String, _ monthly: Double, gb: Int? = nil, deal: String? = nil, other: Bool = false) -> SearchResult {
    var r = SearchResult(plan: plan(carrier, "Plan \(carrier)", gb: gb, price: monthly, deal: deal), monthly: monthly, yearlySavings: 0)
    r.otherPhone = other
    return r
}
let aiJSON = (try! JSONSerialization.jsonObject(with: buildAiRequest(
    SearchRequest(lines: 2, dataNeedGb: 15, currentMonthly: 80, includePostpaid: true, freePhoneOnly: true, phone: "iPhone 16", includeOtherPhones: true, lineDataNeeds: [5, nil]),
    tradeInPhone: "iPhone 13", results: [aiResult("B", 40), aiResult("A", 25, gb: 15, deal: "3 months free"), aiResult("Z", 10, other: true)])!)) as! [String: Any]
let aiPlans = aiJSON["plans"] as! [[String: Any]]
let aiNeeds = aiJSON["lineDataNeeds"] as! [Any]
check(aiJSON["includePostpaid"] as? Bool == true && aiJSON["freePhoneOnly"] as? Bool == true && aiJSON["includeOtherPhones"] as? Bool == true
      && aiJSON["tradeInPhone"] as? String == "iPhone 13" && aiNeeds.count == 2 && aiNeeds[0] as? Int == 5 && aiNeeds[1] is NSNull, "AI request: every setting of the search is passed along")
check(aiPlans.count == 2 && aiPlans[0]["carrier"] as? String == "A" && aiPlans[0]["deal"] as? String == "3 months free", "AI request: cheapest first, other-phone deals skipped")
check(aiPlans[1]["dataGb"] is NSNull && aiJSON["lines"] as? Int == 2 && aiJSON["dataNeedGb"] as? Int == 15 && aiJSON["currentMonthly"] as? Double == 80 && aiJSON["phone"] as? String == "iPhone 16", "AI request: search fields and unlimited data")
let aiMany = (try! JSONSerialization.jsonObject(with: buildAiRequest(
    SearchRequest(lines: 1, dataNeedGb: nil, currentMonthly: 0, includePostpaid: false), tradeInPhone: nil, results: (1...30).map { aiResult("C\($0)", Double($0)) })!)) as! [String: Any]
check((aiMany["plans"] as! [Any]).count == aiMaxPlans && aiMany["dataNeedGb"] is NSNull && aiMany["currentMonthly"] == nil && aiMany["phone"] == nil && aiMany["lineDataNeeds"] == nil && aiMany["tradeInPhone"] == nil
      && aiMany["includePostpaid"] as? Bool == false, "AI request: capped, unentered fields left out")
let aiReq = SearchRequest(lines: 1, dataNeedGb: 5, currentMonthly: 0, includePostpaid: true)
check(buildAiRequest(aiReq, tradeInPhone: nil, results: []) == nil
      && buildAiRequest(aiReq, tradeInPhone: nil, results: [aiResult("X", 5, other: true)]) == nil, "AI request: nothing to ask means no request")
check(parseAiText("{\"text\":\"  Look at A first. \"}".data(using: .utf8)!) == "Look at A first."
      && parseAiText("{\"error\":\"AI unavailable.\",\"status\":503}".data(using: .utf8)!) == nil
      && parseAiText("{\"text\":\"   \"}".data(using: .utf8)!) == nil && parseAiText("not json".data(using: .utf8)!) == nil, "AI reply parsing")

// Cars: partial components, tire sets, rounding, input cleaning, catalog tolerance
func row(_ rows: [CompareRow], _ label: String) -> CompareRow { rows.first { $0.label == label }! }
var car_ca = CarPlanEntry(); car_ca.full = "100"; car_ca.oilCost = "50"; car_ca.oilMonths = "6"; car_ca.fuelYear = "1000"; car_ca.tires = "400"; car_ca.keepYears = "4"; car_ca.price = "20000"
var car_cb = CarPlanEntry(); car_cb.fuelYear = "500"; car_cb.keepYears = "4"; car_cb.price = "20000"
let partialRows = compareMyCars(car_ca, car_cb)
let runRow = row(partialRows, "Yearly running cost (full coverage)")
check(runRow.better == nil && runRow.right == "$500 (partial)" && runRow.left.hasSuffix("(partial)"), "partial components: no tick, (partial) on values")
check(row(partialRows, "Total cost over the years you keep it (full coverage)").better == nil
      && row(partialRows, "Cost per year of owning it (full coverage)").better == nil, "partial: total and per-year suppressed")
check(row(partialRows, "Fuel per year").better == 1, "single-component rows still pick a winner")
var car_cc = car_ca; car_cc.fuelYear = "2000"
check(row(compareMyCars(car_ca, car_cc), "Yearly running cost (full coverage)").better == 0, "same components: winner as before")
var car_cd = CarPlanEntry(); car_cd.keepYears = "4"
check(row(compareMyCars(car_cd, car_cb), "Yearly running cost (full coverage)").right == "$500 (partial)"
      && row(compareMyCars(car_cd, car_cb), "Yearly running cost (full coverage)").left == notEntered, "not entered stays unmarked")
var tt = CarPlanEntry(); tt.tires = "400"; tt.tireYears = "3"; tt.keepYears = "9"
check(tt.tiresOverKeep == 1200, "tire sets exact division 9/3 = 3")
tt.tireYears = "0.3"; tt.keepYears = "0.9"
check(tt.tiresOverKeep == 1200, "tire sets float noise 0.9/0.3 stays 3")
tt.tireYears = "3"; tt.keepYears = "4"
check(tt.tiresOverKeep == 800, "tire sets round up")
tt.keepYears = "1"
check(tt.tiresOverKeep == 400, "tire sets at least one")
check(amount(24.5) == "$25" && amount(0.5) == "$1" && amount(2.5) == "$3" && amount(1200) == "$1,200" && amount(nil) == notEntered, "amount half up en_US")
check(whole(24.5) == "24.5" && whole(25) == "25" && whole(nil) == notEntered && whole(24.5, suffix: " MPG") == "24.5 MPG", "whole shows decimal only when fractional")
check(cleanNumberInput("12,50", maxLen: 9) == "12.50" && cleanNumberInput("1.2.3", maxLen: 9) == "1.23", "cleanNumberInput comma and one dot")
check(cleanNumberInput("\u{0663}4a5", maxLen: 9) == "45" && cleanNumberInput("123456789012", maxLen: 9) == "123456789"
      && cleanNumberInput("1234567890", maxLen: 7) == "1234567" && cleanNumberInput("", maxLen: 9) == "", "cleanNumberInput ASCII only and max length")
let pc = Catalog.parse("""
{"phones":[{"id":"p","name":"P","price":"799","display":6.1,"batteryMah":true,"chip":null,"updated":null},{"name":"bad"}],
 "cars":[{"id":"c","name":"C","mpgUnit":null,"updated":null,"mpgCombined":"30","mpgCity":28,"annualFuelCost":null,"url":null}]}
""".data(using: .utf8)!)!
check(pc.phones.count == 1 && pc.phones[0].price == nil && pc.phones[0].display == 6.1 && pc.phones[0].batteryMah == nil
      && pc.phones[0].chip == nil && pc.phones[0].updated == "", "catalog: null/string/bool tolerated, bad entry skipped")
check(pc.cars[0].mpgUnit == "MPG" && pc.cars[0].updated == "" && pc.cars[0].mpgCombined == nil
      && pc.cars[0].mpgCity == 28 && pc.cars[0].url == "", "catalog car: null defaults, numeric string rejected")

// ---- Plans logic fixes (ISSUE-4..7, 29-31; mirrors LiveDealsTest.kt / PhoneMatchTest.kt)
check(dealIsBetterPhone("Free iPhone Air", phone: "iPhone 17 Pro"), "Air newer than 17")
check(!dealIsBetterPhone("Free iPhone Air", phone: "iPhone 18"), "Air not better than 18")
check(!dealIsBetterPhone("Free iPhone 17 Pro", phone: "iPhone Air"), "17 not better than Air")
check(dealIsBetterPhone("iPhone 11 deal", phone: "iPhone XS"), "11 better than XS")
check(!dealIsBetterPhone("iPhone XR deal", phone: "iPhone 11"), "XR not better than 11")
check(!dealIsBetterPhone("iPhone XS Max deal", phone: "iPhone 12"), "XS not better than 12")
check(!dealIsBetterPhone("iPhone 16e for $0", phone: "iPhone 16"), "16e not better than 16")
check(dealIsBetterPhone("iPhone 16e for $0", phone: "iPhone 15"), "16e better than 15")
check(dealIsBetterPhone("iPhone 16 for $0", phone: "iPhone 16e"), "16 better than 16e")
check(dealIsBetterPhone("iPhone Duo", phone: "iPhone Air"), "Duo newest")
let dj = """
{"deals":[{"carrier":"T-Mobile","deal":"A","freePhone":true,"requiresPlans":["Experience"]},{"deal":"no carrier"},
{"carrier":"Mint Mobile","deal":"Promo","freePhone":true,"expires":"2026-10-22"},
{"carrier":"Visible","deal":"P","freePhone":true,"ignoreFreePhone":true,"extras":{"tradein":{"text":"t"},"activation":{"text":"a"}}}]}
""".data(using: .utf8)!
check(parseLiveDeals(dj, phone: nil, today: "2026-10-01")!.map { $0.carrier } == ["T-Mobile", "Mint Mobile", "Visible"], "bad entry skipped")
check(parseLiveDeals(dj, phone: nil, today: "2026-10-31")!.map { $0.carrier } == ["T-Mobile", "Visible"], "expired skipped")
check(dealExpired("2026-10-22", today: "2026-10-23") && !dealExpired("2026-10-22", today: "2026-10-22") && !dealExpired("", today: "2026-10-31"), "dealExpired")
let visDeal = parseLiveDeals(dj, phone: nil, today: "2026-10-01")!.first { $0.carrier == "Visible" }!
check(!visDeal.freePhone, "ignoreFreePhone")
check(visDeal.extras == ["Activation: a", "Trade-in: t"], "extras labels sorted")
func staleResult(_ c: String, _ n: String) -> SearchResult {
    var p = PlanData.plans.first { $0.carrier == c }!
    p.name = n; p.deal = "old"; p.freePhoneOffer = true; p.dealUrl = "u"; p.dealDate = "d"
    return SearchResult(plan: p, monthly: 10, yearlySavings: 0)
}
let attLive = LiveDeal(carrier: "AT&T", deal: "new", freePhone: true, url: "", plans: [], updated: "", linesNote: "", linesUpdated: "", extras: [], activationFee: nil, tradeInMax: nil, requiresPlans: ["Premium", "Elite"])
let mg = mergeLiveDeals([staleResult("AT&T", "Value 2.0"), staleResult("AT&T", "Premium 2.0"), staleResult("Cricket", "X")], [attLive])
check(mg[0].plan.deal == nil && !mg[0].plan.freePhoneOffer && mg[0].plan.dealUrl == "" && mg[0].plan.dealDate == "", "uncovered plan cleared")
check(mg[1].plan.deal == "new" && mg[1].liveDeal, "covered plan gets live deal")
check(mg[2].plan.deal == "old", "carrier without live deals keeps bundled")
check(mergeLiveDeals([staleResult("AT&T", "Value 2.0")], nil)[0].plan.deal == "old", "offline enforces nothing")

print("\(count - failures) of \(count) checks passed" + (failures == 0 ? "" : ", \(failures) FAILED"))
exit(failures == 0 ? 0 : 1)
