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
                    Picker("Lines", selection: $lines) {
                        ForEach(1...4, id: \.self) { Text($0 == 4 ? "4+" : "\($0)").tag($0) }
                    }.pickerStyle(SegmentedPickerStyle())

                    Text("Data per line").font(.headline)
                    Picker("Data", selection: $dataIdx) {
                        ForEach(0..<dataChoices.count, id: \.self) { Text(dataChoices[$0].label).tag($0) }
                    }.pickerStyle(SegmentedPickerStyle())

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
                    if list.isEmpty {
                        Text("No plans matched. Try more data or include big carriers.")
                    } else {
                        ForEach(list) { card($0) }
                    }
                    Text("Prices researched Oct 2026 and may have changed - confirm on the carrier's site. PlanSaver may earn a commission if you sign up through a link.")
                        .font(.footnote).padding(.vertical, 16)
                }.padding(.horizontal, 16)
            }
        }
    }

    private func card(_ r: SearchResult) -> some View {
        let showSavings = Double(current) != nil
        return VStack(alignment: .leading, spacing: 4) {
            Text("\(r.plan.carrier) - \(r.plan.name)").bold()
            Text("\(r.plan.dataGb.map { "\($0) GB" } ?? "Unlimited") - \(r.plan.network) network - \(r.plan.prepaid ? "prepaid" : "postpaid")")
                .font(.footnote)
            if !r.plan.note.isEmpty { Text(r.plan.note).font(.footnote) }
            if let deal = r.plan.deal {
                Text((r.liveDeal ? "Live " : "") + (r.otherPhone ? "better phone deal: " : (r.plan.freePhoneOffer ? "phone deal: " : "deal: ")) + deal)
                    .font(.footnote).foregroundColor(.orange)
                if r.plan.freePhoneOffer {
                    Text("Usually needs a trade-in and 24-36 months of bill credits - leaving early forfeits them.")
                        .font(.caption2)
                }
            }
            if let url = URL(string: r.plan.dealUrl), !r.plan.dealUrl.isEmpty { Link("See the offer", destination: url) }
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
