import SwiftUI

private let dataChoices: [(label: String, gb: Int?)] = [("1-5 GB", 5), ("10 GB", 10), ("25 GB", 25), ("Unlimited", nil)]

struct ContentView: View {
    @State private var lines = 1
    @State private var dataIdx = 3
    @State private var current = ""
    @State private var postpaid = true
    @State private var freePhone = false
    @State private var brand = "iPhone"
    @State private var familyIdx = 3
    @State private var variant = ""
    @State private var otherPhones = false
    @State private var loading = false
    @State private var results: [SearchResult]? = nil
    @State private var searchedPhone: String? = nil
    @State private var sortByData = false
    @State private var searchedLines = 1

    private var families: [PhoneFamily] { Phones.brands[brand] ?? [] }
    private var family: PhoneFamily { families[min(familyIdx, families.count - 1)] }
    private var versionLabel: String {
        "v" + (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?")
    }

    var body: some View {
        if let results = results {
            resultsScreen(results)
        } else {
            formScreen
        }
    }

    // MARK: Search form

    private var formScreen: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("PlanSaver").font(.largeTitle).bold()
                    Text("Find the cheapest US phone plan for what you actually use.").font(.subheadline)

                    Text("How many lines?").font(.headline)
                    Stepper(value: $lines, in: 1...6) { Text("\(lines) \(lines == 1 ? "line" : "lines")") }

                    Text("Data per line").font(.headline)
                    Picker("Data", selection: $dataIdx) {
                        ForEach(0..<dataChoices.count, id: \.self) { Text(dataChoices[$0].label).tag($0) }
                    }.pickerStyle(MenuPickerStyle())

                    Text("Your current phone bill (all lines, per month)").font(.headline)
                    HStack {
                        Text("$")
                        TextField("0", text: $current)
                            .keyboardType(.decimalPad)
                            .onChange(of: current) { v in
                                let f = String(v.filter { $0.isNumber || $0 == "." }.prefix(7))
                                if f != v { current = f }
                            }
                    }
                    .padding(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.5)))

                    Toggle("Include big-carrier and cable plans (Verizon, AT&T, T-Mobile, Xfinity, Spectrum)", isOn: $postpaid)
                    Toggle("Only carriers offering a free or discounted phone", isOn: $freePhone)

                    if freePhone { phonePicker }

                    Button(action: search) {
                        Text(loading ? "Searching..." : "Search plans")
                            .frame(maxWidth: .infinity).padding().background(loading ? Color.gray : Color.blue)
                            .foregroundColor(.white).cornerRadius(10)
                    }.disabled(loading)
                    if loading { ProgressView() }
                }
                .padding(16)
            }
            Text(versionLabel).font(.caption2).foregroundColor(.secondary).padding(8)
        }
    }

    private var phonePicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Which phone do you want?").font(.headline)
            Picker("Brand", selection: $brand) {
                ForEach(Phones.brandOrder, id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(SegmentedPickerStyle())
            .onChange(of: brand) { _ in familyIdx = 0; variant = "" }

            Picker("Model", selection: $familyIdx) {
                ForEach(0..<families.count, id: \.self) { Text(families[$0].label).tag($0) }
            }
            .pickerStyle(MenuPickerStyle())
            .onChange(of: familyIdx) { _ in if !family.variants.contains(variant) { variant = "" } }

            Picker("Version", selection: $variant) {
                ForEach(family.variants, id: \.self) { Text($0.isEmpty ? "Base" : $0.trimmingCharacters(in: .whitespaces)).tag($0) }
            }.pickerStyle(SegmentedPickerStyle())
            Text("Selected: " + family.name(variant)).font(.footnote)
            Toggle("Check for better phones as well", isOn: $otherPhones)
        }
    }

    private func search() {
        loading = true
        let phone = freePhone ? family.name(variant) : nil
        let request = SearchRequest(lines: lines, dataNeedGb: dataChoices[dataIdx].gb,
                                    currentMonthly: Double(current) ?? 0, includePostpaid: postpaid,
                                    freePhoneOnly: freePhone, phone: phone, includeOtherPhones: otherPhones)
        PlanRepository.search(request) { found in
            searchedPhone = phone
            searchedLines = lines
            results = found
            loading = false
        }
    }

    // MARK: Results

    private func resultsScreen(_ list: [SearchResult]) -> some View {
        VStack(spacing: 0) {
            HStack {
                Button("< Back") { results = nil }
                Text("Results").font(.title2).bold()
                Spacer()
            }.padding(16)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    if let phone = searchedPhone {
                        Text("Phone: \(phone). Carriers below advertise phone deals, but whether \(phone) is covered varies by carrier and changes weekly - confirm on their site.")
                            .font(.footnote)
                    }
                    Picker("Sort", selection: $sortByData) {
                        Text("Lowest price").tag(false)
                        Text("Most data").tag(true)
                    }.pickerStyle(SegmentedPickerStyle())
                    if lines > 4 {
                        Text("For more than 4 lines the 4-line price per line is used, so totals are estimates.")
                            .font(.footnote)
                    }
                    if list.isEmpty {
                        Text("No plans matched. Try more data or include big carriers.")
                    } else {
                        ForEach(sorted(list)) { card($0, lines: searchedLines) }
                    }
                    Text("Prices researched Oct 2026 from third-party roundups, not checked with the carriers, and may have changed - confirm on the carrier's site. PlanSaver may earn a commission if you sign up through a link.")
                        .font(.footnote).padding(.vertical, 16)
                }.padding(.horizontal, 16)
            }
        }
    }

    /// Better-phone results stay last; inside each group order by price, or by data (unlimited first) then price.
    private func sorted(_ list: [SearchResult]) -> [SearchResult] {
        func data(_ r: SearchResult) -> Int { sortByData ? -(r.plan.dataGb ?? 1_000_000) : 0 }
        return list.sorted {
            if $0.otherPhone != $1.otherPhone { return !$0.otherPhone }
            if data($0) != data($1) { return data($0) < data($1) }
            return $0.monthly < $1.monthly
        }
    }

    private func card(_ r: SearchResult, lines: Int) -> some View {
        let showSavings = Double(current) != nil
        return VStack(alignment: .leading, spacing: 4) {
            Text("\(r.plan.carrier) - \(r.plan.name)").bold()
            Text("\(r.plan.dataGb.map { "\($0) GB" } ?? "Unlimited") - \(r.plan.network) network - \(r.plan.prepaid ? "prepaid" : "postpaid")")
                .font(.footnote)
            if !r.plan.note.isEmpty { Text(r.plan.note).font(.footnote) }
            if let deal = r.plan.deal {
                Text((r.liveDeal ? "Live " : "") + (r.otherPhone ? "better phone deal: " : (r.plan.freePhoneOffer ? "phone deal: " : "deal: ")) + deal
                     + (r.plan.dealDate.isEmpty ? "" : " (as of \(r.plan.dealDate))"))
                    .font(.footnote).foregroundColor(.orange)
                if r.plan.freePhoneOffer {
                    Text("Usually needs a trade-in and 24-36 months of bill credits - leaving early forfeits them.")
                        .font(.caption2)
                }
            }
            if r.plan.dealUrl.hasPrefix("https://"), let url = URL(string: r.plan.dealUrl) { Link("See the offer", destination: url) }
            if lines > 1 && r.plan.pricePerLine[lines] == nil {
                Text("Estimate: no \(lines)-line price on file, so this uses the nearest known per-line price.").font(.footnote)
            }
            ForEach(coverageLinks(r.plan.network), id: \.name) { c in
                if let url = URL(string: c.url) { Link("\(c.name) coverage map", destination: url) }
            }
            Text(String(format: "$%.2f / month", r.monthly)).font(.headline)
            if r.plan.termMonths > 1 {
                Text(String(format: "Pay $%.0f up front for %d months", r.monthly * Double(r.plan.termMonths), r.plan.termMonths))
                    .font(.footnote)
            }
            if showSavings && r.yearlySavings > 0 {
                Text(String(format: "Save about $%.0f a year", r.yearlySavings)).foregroundColor(.blue)
            }
            if let url = URL(string: r.plan.signupUrl) { Link("View plan", destination: url) }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.gray.opacity(0.12))
        .cornerRadius(10)
    }
}
