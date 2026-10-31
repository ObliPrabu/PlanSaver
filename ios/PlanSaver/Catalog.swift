import Foundation

/// Phones, cars and car insurers for the Insurance / Phones / Cars tabs. deals-data/catalog.json holds only names; the
/// catalog robot (tools/refresh_catalog.py) fills every number and records the page each came from, so any field can be
/// nil ("not found yet"). Nothing in the app invents a figure. Mirrors Android's Catalog.kt.
struct CatalogPhone: Identifiable {
    let id: String
    let name: String
    let url: String
    let price: Double?
    let display: Double?
    let batteryMah: Int?
    let chip: String?
    let storage: String?
    let updated: String
    let sources: [String: String]
}

struct CatalogCar: Identifiable {
    let id: String
    let name: String
    let url: String
    let price: Double?
    let mpgCombined: Int?
    let mpgCity: Int?
    let mpgHighway: Int?
    let mpgUnit: String
    let fuelType: String?
    let bodyType: String?
    let annualFuelCost: Int?
    let rangeMiles: Int?
    let updated: String
    let sources: [String: String]
}

struct CatalogInsurer: Identifiable {
    let id: String
    let name: String
    let url: String
    let fullMonthly: Double?
    let minMonthly: Double?
    let updated: String
    let sources: [String: String]

    func monthly(full: Bool) -> Double? { full ? fullMonthly : minMonthly }
}

struct Catalog {
    var phones: [CatalogPhone] = []
    var cars: [CatalogCar] = []
    var insurers: [CatalogInsurer] = []

    private static func num(_ o: [String: Any], _ k: String) -> Double? { (o[k] as? NSNumber)?.doubleValue }
    private static func int(_ o: [String: Any], _ k: String) -> Int? { (o[k] as? NSNumber)?.intValue }
    private static func str(_ o: [String: Any], _ k: String) -> String? {
        guard let s = o[k] as? String, !s.isEmpty else { return nil }
        return s
    }
    private static func srcs(_ o: [String: Any]) -> [String: String] { o["sources"] as? [String: String] ?? [:] }

    static func parse(_ data: Data) -> Catalog? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        func items(_ key: String) -> [[String: Any]] { root[key] as? [[String: Any]] ?? [] }
        return Catalog(
            phones: items("phones").compactMap { o in
                guard let id = o["id"] as? String, let name = o["name"] as? String else { return nil }
                return CatalogPhone(id: id, name: name, url: o["url"] as? String ?? "", price: num(o, "price"),
                                    display: num(o, "display"), batteryMah: int(o, "batteryMah"), chip: str(o, "chip"),
                                    storage: str(o, "storage"), updated: o["updated"] as? String ?? "", sources: srcs(o))
            },
            cars: items("cars").compactMap { o in
                guard let id = o["id"] as? String, let name = o["name"] as? String else { return nil }
                return CatalogCar(id: id, name: name, url: o["url"] as? String ?? "", price: num(o, "price"),
                                  mpgCombined: int(o, "mpgCombined"), mpgCity: int(o, "mpgCity"), mpgHighway: int(o, "mpgHighway"),
                                  mpgUnit: o["mpgUnit"] as? String ?? "MPG", fuelType: str(o, "fuelType"), bodyType: str(o, "bodyType"),
                                  annualFuelCost: int(o, "annualFuelCost"), rangeMiles: int(o, "rangeMiles"),
                                  updated: o["updated"] as? String ?? "", sources: srcs(o))
            },
            insurers: items("insurers").compactMap { o in
                guard let id = o["id"] as? String, let name = o["name"] as? String else { return nil }
                return CatalogInsurer(id: id, name: name, url: o["url"] as? String ?? "", fullMonthly: num(o, "fullMonthly"),
                                      minMonthly: num(o, "minMonthly"), updated: o["updated"] as? String ?? "", sources: srcs(o))
            })
    }
}

/// Hosted catalog.json (refreshed by the repo's catalog robot). Falls back to the copy bundled in the app.
let catalogURL = "https://raw.githubusercontent.com/ObliPrabu/PlanSaver/main/deals-data/catalog.json"

enum CatalogRepository {
    static func load(completion: @escaping (Catalog) -> Void) {
        func bundled() -> Catalog {
            guard let url = Bundle.main.url(forResource: "catalog", withExtension: "json"),
                  let data = try? Data(contentsOf: url) else { return Catalog() }
            return Catalog.parse(data) ?? Catalog()
        }
        guard let url = URL(string: catalogURL) else { return completion(bundled()) }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.httpMethod = "GET"
        URLSession.shared.dataTask(with: req) { data, response, _ in
            let live = (response as? HTTPURLResponse)?.statusCode == 200 ? data.flatMap { Catalog.parse($0) } : nil
            DispatchQueue.main.async { completion(live ?? bundled()) }
        }.resume()
    }
}

/// One row of a side-by-side comparison. `better` is 0 (left), 1 (right) or nil when neither is clearly better.
struct CompareRow {
    let label: String
    let left: String
    let right: String
    let better: Int?
}

let notFound = "not found yet"

func winner<T: Comparable>(_ a: T?, _ b: T?, lowerIsBetter: Bool) -> Int? {
    guard let a = a, let b = b, a != b else { return nil }
    return ((a < b) == lowerIsBetter) ? 0 : 1
}

private func money(_ v: Double?) -> String {
    guard let v = v else { return notFound }
    let f = NumberFormatter()
    f.numberStyle = .decimal
    f.maximumFractionDigits = 0
    return "$" + (f.string(from: NSNumber(value: v)) ?? String(Int(v)))
}

func comparePhones(_ a: CatalogPhone, _ b: CatalogPhone) -> [CompareRow] {
    [
        CompareRow(label: "Starting price", left: money(a.price), right: money(b.price), better: winner(a.price, b.price, lowerIsBetter: true)),
        CompareRow(label: "Display", left: a.display.map { "\($0) in" } ?? notFound, right: b.display.map { "\($0) in" } ?? notFound, better: nil),
        CompareRow(label: "Battery", left: a.batteryMah.map { "\($0) mAh" } ?? notFound, right: b.batteryMah.map { "\($0) mAh" } ?? notFound,
                   better: winner(a.batteryMah, b.batteryMah, lowerIsBetter: false)),
        CompareRow(label: "Chip", left: a.chip ?? notFound, right: b.chip ?? notFound, better: nil),
        CompareRow(label: "Storage", left: a.storage ?? notFound, right: b.storage ?? notFound, better: nil),
    ]
}

func compareCars(_ a: CatalogCar, _ b: CatalogCar) -> [CompareRow] {
    func mpg(_ c: CatalogCar) -> String { c.mpgCombined.map { "\($0) \(c.mpgUnit)" } ?? notFound }
    func cityHwy(_ c: CatalogCar) -> String {
        let parts = [c.mpgCity, c.mpgHighway].compactMap { $0 }.map(String.init)
        return parts.isEmpty ? notFound : parts.joined(separator: " / ")
    }
    let sameUnit = a.mpgUnit == b.mpgUnit // MPG and MPGe are not the same thing, so no winner across them
    return [
        CompareRow(label: "Starting price", left: money(a.price), right: money(b.price), better: winner(a.price, b.price, lowerIsBetter: true)),
        CompareRow(label: "Fuel economy (combined)", left: mpg(a), right: mpg(b),
                   better: sameUnit ? winner(a.mpgCombined, b.mpgCombined, lowerIsBetter: false) : nil),
        CompareRow(label: "City / highway", left: cityHwy(a), right: cityHwy(b), better: nil),
        CompareRow(label: "Fuel cost per year (EPA estimate)", left: money(a.annualFuelCost.map(Double.init)),
                   right: money(b.annualFuelCost.map(Double.init)), better: winner(a.annualFuelCost, b.annualFuelCost, lowerIsBetter: true)),
        CompareRow(label: "Electric range", left: a.rangeMiles.map { "\($0) mi" } ?? "-", right: b.rangeMiles.map { "\($0) mi" } ?? "-",
                   better: winner(a.rangeMiles, b.rangeMiles, lowerIsBetter: false)),
        CompareRow(label: "Body type", left: a.bodyType ?? notFound, right: b.bodyType ?? notFound, better: nil),
        CompareRow(label: "Fuel", left: a.fuelType ?? notFound, right: b.fuelType ?? notFound, better: nil),
    ]
}

/// Insurers with a figure first, cheapest first; those with none follow in catalog order.
func sortInsurers(_ list: [CatalogInsurer], full: Bool) -> [CatalogInsurer] {
    let with = list.filter { $0.monthly(full: full) != nil }.sorted { $0.monthly(full: full)! < $1.monthly(full: full)! }
    return with + list.filter { $0.monthly(full: full) == nil }
}

/// Where a number came from, shown next to it: the page's domain, e.g. "nerdwallet.com".
func sourceDomain(_ url: String?) -> String? {
    guard let url = url, let host = URL(string: url)?.host else { return nil }
    return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
}
