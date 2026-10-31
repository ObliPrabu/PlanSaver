import SwiftUI
import UIKit

private let dataChoices: [(label: String, gb: Int?)] = [("1-5 GB", 5), ("10 GB", 10), ("25 GB", 25), ("Unlimited", nil)]

/// Shown as a floating top-right toggle over the whole screen - matches the
/// Android build's manual override (see Theme.kt's ThemeMode) and the same
/// top-right placement BuddyVerse uses, rather than relying only on the
/// system appearance setting.
enum ThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
    var toggleLabel: String {
        switch self {
        case .system: return "🌓 Auto"
        case .light: return "☀️ Light"
        case .dark: return "🌙 Dark"
        }
    }
}

struct ContentView: View {
    @State private var lines = 1
    @State private var dataIdx = 3
    @State private var customLines = false
    @State private var lineNeeds = Array(repeating: 3, count: 6) // data choice index of each line
    @State private var current = ""
    @State private var postpaid = true
    @State private var freePhone = false
    @State private var brand = "iPhone"
    @State private var familyIdx = Phones.defaultIphoneIdx
    @State private var variant = ""
    @State private var otherPhones = false
    @State private var quizOpen = false
    @State private var quizUsage = -1 // -1 = not answered yet
    @State private var quizWifi = true
    @State private var quizHotspot = false
    @State private var tradeIn = false
    @State private var oldBrand = "iPhone"
    @State private var oldFamilyIdx = Phones.defaultIphoneIdx
    @State private var oldVariant = ""
    @State private var totalOpen: Set<UUID> = []
    @State private var loading = false
    @State private var results: [SearchResult]? = nil
    @State private var searchedPhone: String? = nil
    @State private var sortByData = false
    @State private var filters = ResultFilters()
    @State private var searchedLines = 1
    @State private var aiText: String? = nil   // the AI suggestion for the current results (AiSuggest.swift)
    @State private var aiLoading = false
    @State private var aiRun = 0               // a newer search makes an older, slower AI reply be ignored

    private var families: [PhoneFamily] { Phones.brands[brand] ?? [] }
    private var family: PhoneFamily { families[min(familyIdx, families.count - 1)] }
    private var oldFamilies: [PhoneFamily] { Phones.brands[oldBrand] ?? [] }
    private var oldFamily: PhoneFamily { oldFamilies[min(oldFamilyIdx, oldFamilies.count - 1)] }
    private var oldPhone: String { oldFamily.name(oldVariant) }
    private var versionLabel: String {
        "v" + (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?")
    }

    var body: some View {
        // The theme toggle floats over every tab, so it lives in RootView.
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
                    Text("PlanSaver").font(.largeTitle).bold().padding(.trailing, 80)
                    Text("Find the cheapest US phone plan for what you actually use.").font(.subheadline)

                    Text("How many lines?").font(.headline)
                    Stepper(value: $lines, in: 1...6) { Text("\(lines) \(lines == 1 ? "line" : "lines")") }

                    if lines > 1 {
                        Toggle("Different data for each line", isOn: $customLines)
                    }
                    if customLines && lines > 1 {
                        ForEach(0..<lines, id: \.self) { k in
                            HStack {
                                Text("Line \(k + 1)").font(.headline)
                                Spacer()
                                Picker("Line \(k + 1) data", selection: $lineNeeds[k]) {
                                    ForEach(0..<dataChoices.count, id: \.self) { Text(dataChoices[$0].label).tag($0) }
                                }.pickerStyle(MenuPickerStyle())
                            }
                        }
                    } else {
                        Text("Data per line").font(.headline)
                        Picker("Data", selection: $dataIdx) {
                            ForEach(0..<dataChoices.count, id: \.self) { Text(dataChoices[$0].label).tag($0) }
                        }.pickerStyle(MenuPickerStyle())
                        Button(quizOpen ? "Hide the quiz" : "Not sure? Answer 3 quick questions") { quizOpen.toggle() }
                        if quizOpen { quiz }
                    }

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
                .frame(maxWidth: contentMaxWidth) // readable width on an iPad; iPhones are narrower, so nothing changes
                .frame(maxWidth: .infinity)
            }
            Text(versionLabel).font(.caption2).foregroundColor(.secondary).padding(8)
        }
    }

    private func moneyField(_ label: String, text: Binding<String>) -> some View {
        HStack {
            Text("$")
            TextField(label, text: text)
                .keyboardType(.decimalPad)
                .onChange(of: text.wrappedValue) { v in
                    let f = String(v.filter { $0.isNumber || $0 == "." }.prefix(7))
                    if f != v { text.wrappedValue = f }
                }
        }
        .padding(8)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.5)))
    }

    private func applyQuiz() { if quizUsage >= 0 { dataIdx = quizDataIndex(usage: quizUsage, homeWifi: quizWifi, hotspot: quizHotspot) } }

    private var quiz: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("What do you do most when you're not on Wi-Fi?").font(.headline)
            Picker("Usage", selection: $quizUsage) {
                Text("Pick one").tag(-1)
                Text("Texts and reading").tag(0)
                Text("Music and social").tag(1)
                Text("Streaming video").tag(2)
            }
            .pickerStyle(MenuPickerStyle())
            .onChange(of: quizUsage) { _ in applyQuiz() }
            Toggle("I have Wi-Fi at home", isOn: $quizWifi).onChange(of: quizWifi) { _ in applyQuiz() }
            Toggle("I share my data as a hotspot", isOn: $quizHotspot).onChange(of: quizHotspot) { _ in applyQuiz() }
            Text(quizUsage < 0 ? "Pick what you do most and I'll choose the data for you."
                 : "A rough guess: \(dataChoices[dataIdx].label) per line. You can still change it above.")
                .font(.footnote)
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

            // Five variants (e.g. Pixel 10 Pro Fold) don't fit a segmented control at big text sizes, so use a menu.
            if family.variants.count > 4 {
                Picker("Version", selection: $variant) {
                    ForEach(family.variants, id: \.self) { Text($0.isEmpty ? "Base" : $0.trimmingCharacters(in: .whitespaces)).tag($0) }
                }.pickerStyle(MenuPickerStyle())
            } else {
                Picker("Version", selection: $variant) {
                    ForEach(family.variants, id: \.self) { Text($0.isEmpty ? "Base" : $0.trimmingCharacters(in: .whitespaces)).tag($0) }
                }.pickerStyle(SegmentedPickerStyle())
            }
            Text("Selected: " + family.name(variant)).font(.footnote)
            Toggle("Check for better phones as well", isOn: $otherPhones)
            // A trade-in only comes with buying a new phone, so it is only offered here.
            Toggle("I'll trade in my old phone", isOn: $tradeIn)
            if tradeIn {
                Text("Which phone are you trading in?").font(.headline)
                Picker("Old brand", selection: $oldBrand) {
                    ForEach(Phones.brandOrder, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(SegmentedPickerStyle())
                .onChange(of: oldBrand) { _ in oldFamilyIdx = 0; oldVariant = "" }
                Picker("Old model", selection: $oldFamilyIdx) {
                    ForEach(0..<oldFamilies.count, id: \.self) { Text(oldFamilies[$0].label).tag($0) }
                }
                .pickerStyle(MenuPickerStyle())
                .onChange(of: oldFamilyIdx) { _ in if !oldFamily.variants.contains(oldVariant) { oldVariant = "" } }
                Picker("Old version", selection: $oldVariant) {
                    ForEach(oldFamily.variants, id: \.self) { Text($0.isEmpty ? "Base" : $0.trimmingCharacters(in: .whitespaces)).tag($0) }
                }.pickerStyle(MenuPickerStyle())
                Text("Trading in: " + oldFamily.name(oldVariant)).font(.footnote)
            }
        }
    }

    private func search() {
        loading = true
        let phone = freePhone ? family.name(variant) : nil
        let request = SearchRequest(lines: lines, dataNeedGb: dataChoices[dataIdx].gb,
                                    currentMonthly: Double(current) ?? 0, includePostpaid: postpaid,
                                    freePhoneOnly: freePhone, phone: phone, includeOtherPhones: otherPhones,
                                    lineDataNeeds: (customLines && lines > 1) ? (0..<lines).map { dataChoices[lineNeeds[$0]].gb } : nil)
        PlanRepository.search(request) { found in
            searchedPhone = phone
            searchedLines = lines
            results = found
            loading = false
            // The AI suggestion arrives after the normal results are already on screen; if it fails, nothing is shown.
            aiText = nil
            aiRun += 1
            let myRun = aiRun
            let tradeInName: String? = (freePhone && tradeIn) ? oldFamily.name(oldVariant) : nil
            if let body = buildAiRequest(request, tradeInPhone: tradeInName, results: found) {
                aiLoading = true
                fetchAiSuggestion(body) { text in
                    if myRun == aiRun { aiText = text; aiLoading = false }
                }
            } else { aiLoading = false }
        }
    }

    // MARK: Results

    private func resultsScreen(_ all: [SearchResult]) -> some View {
        let list = applyFilters(all, filters)
        return VStack(spacing: 0) {
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
                    // With every plan on the same data (e.g. only unlimited plans), "Most data" would look identical to "Lowest price".
                    if Set(list.map { $0.plan.dataGb }).count > 1 {
                        Picker("Sort", selection: $sortByData) {
                            Text("Lowest price").tag(false)
                            Text("Most data").tag(true)
                        }.pickerStyle(SegmentedPickerStyle())
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Text("Filter:").font(.footnote)
                            ForEach(["Verizon", "AT&T", "T-Mobile"], id: \.self) { n in
                                chip(n, on: filters.networks.contains(n)) {
                                    if filters.networks.contains(n) { filters.networks.remove(n) } else { filters.networks.insert(n) }
                                }
                            }
                            chip("Prepaid", on: filters.type == 1) { filters.type = filters.type == 1 ? 0 : 1 }
                            chip("Postpaid", on: filters.type == 2) { filters.type = filters.type == 2 ? 0 : 2 }
                            chip("Has a deal", on: filters.dealOnly) { filters.dealOnly.toggle() }
                            chip("Unlimited only", on: filters.unlimitedOnly) { filters.unlimitedOnly.toggle() }
                        }
                    }
                    if !all.isEmpty { Text("Showing \(list.count) of \(all.count) plans").font(.footnote) }
                    if lines > 4 {
                        Text("For more than 4 lines the 4-line price per line is used, so totals are estimates.")
                            .font(.footnote)
                    }
                    if aiLoading { Text("Asking AI for a suggestion...").font(.footnote) }
                    if let suggestion = aiText {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("AI suggestion").bold()
                            Text(suggestion)
                            Text("Written by Google's Gemini from the cheapest plans found below. Your search (lines, data, current bill and phone) is sent to it. Prices are unverified, so confirm on the carrier's site.")
                                .font(.caption2)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(10)
                    }
                    if list.isEmpty {
                        Text(all.isEmpty ? "No plans matched. Try more data or include big carriers."
                             : "No plans match these filters. Turn one off to see more.")
                    } else {
                        let picks = topPicks(list)
                        if !picks.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Top picks").bold()
                                ForEach(picks) { p in
                                    Text(String(format: "%@: %@ - %@ ($%.2f / month)", p.label, p.result.plan.carrier,
                                                p.result.plan.name, p.result.monthly)).font(.footnote)
                                }
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(10)
                        }
                        ForEach(sorted(list)) { card($0, lines: searchedLines) }
                    }
                    Text("Prices researched Oct 2026 from third-party roundups, not checked with the carriers, and may have changed - confirm on the carrier's site. PlanSaver may earn a commission if you sign up through a link.")
                        .font(.footnote).padding(.vertical, 16)
                }
                .padding(.horizontal, 16)
                .frame(maxWidth: contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func chip(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.footnote)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(on ? Color.blue : Color(UIColor.secondarySystemBackground))
                .foregroundColor(on ? .white : .primary)
                .clipShape(Capsule())
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
            if let picks = r.assignments {
                Text("\(r.plan.carrier) - a plan for each line").bold()
                ForEach(picks) { a in
                    Text(String(format: "Line %d: %@ (%@) - $%.2f", a.line, a.plan.name,
                                a.plan.dataGb.map { "\($0) GB" } ?? "Unlimited", a.price)).font(.footnote)
                }
                Text("Check that \(r.plan.carrier) lets you mix plans on one account.").font(.caption2)
            } else {
                Text("\(r.plan.carrier) - \(r.plan.name)").bold()
                Text("\(r.plan.dataGb.map { "\($0) GB" } ?? "Unlimited") - \(r.plan.network) network - \(r.plan.prepaid ? "prepaid" : "postpaid")")
                    .font(.footnote)
                if !r.plan.note.isEmpty { Text(r.plan.note).font(.footnote) }
            }
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
            if lines > 1 && r.assignments == nil && r.plan.pricePerLine[lines] == nil {
                Text("Estimate: no \(lines)-line price on file, so this uses the nearest known per-line price.").font(.footnote)
            }
            if lines > 1 && !r.plan.linesNote.isEmpty {
                Text("Multi-line price found online" + (r.plan.linesDate.isEmpty ? "" : " (as of \(r.plan.linesDate))")
                     + ", unverified: " + r.plan.linesNote)
                    .font(.footnote).foregroundColor(.orange)
            }
            if !r.plan.extraNotes.isEmpty {
                Text("Found online for \(r.plan.carrier) (unverified; may differ by plan):").font(.caption2)
                ForEach(r.plan.extraNotes, id: \.self) { Text($0).font(.footnote) }
            }
            ForEach(coverageLinks(r.plan.network), id: \.name) { c in
                if let url = URL(string: c.url) { Link("\(c.name) coverage map", destination: url) }
            }
            Text(String(format: "$%.2f / month", r.monthly)).font(.headline)
            if r.plan.termMonths > 1 && r.assignments == nil {
                Text(String(format: "Pay $%.0f up front for %d months", r.monthly * Double(r.plan.termMonths), r.plan.termMonths))
                    .font(.footnote)
            }
            if showSavings && r.yearlySavings > 0 {
                Text(String(format: "Save about $%.0f a year", r.yearlySavings)).foregroundColor(.blue)
            }
            Button(totalOpen.contains(r.id) ? "Hide my total cost" : "How much will I pay in total?") {
                if totalOpen.contains(r.id) { totalOpen.remove(r.id) } else { totalOpen.insert(r.id) }
            }
            if totalOpen.contains(r.id) {
                let tradingIn = freePhone && tradeIn
                let activation = (r.plan.activationFee ?? 0) * Double(lines)
                let old = oldPhone.trimmingCharacters(in: .whitespaces)
                ForEach([12, 24, 36], id: \.self) { m in
                    let c = totalCost(monthly: r.monthly, months: m, phonePrice: 0, tradeIn: 0, activation: activation)
                    Text(String(format: "%d months: plan $%.0f", m, c.plan)
                         + (activation > 0 ? String(format: " + activation $%.0f", activation) : "")
                         + String(format: " = $%.0f", c.total)).font(.footnote)
                }
                if let fee = r.plan.activationFee {
                    Text(String(format: "Activation fee found online: $%.0f per line, so $%.0f for %d line%@ (unverified; some carriers waive it).", fee, activation, lines, lines == 1 ? "" : "s"))
                        .font(.caption2)
                } else {
                    Text("No activation fee found for \(r.plan.carrier) yet, so none is counted - ask the carrier.").font(.caption2)
                }
                Text("The phone itself is not counted here.").font(.caption2)
                if tradingIn {
                    if let max = r.plan.tradeInMax {
                        Text(String(format: "Trade-in%@: this carrier's own text says up to $%.0f. That is a best case; what your phone gets depends on its model and condition, so confirm with the carrier.",
                                    old.isEmpty ? "" : " of \(old)", max)).font(.caption2)
                    } else {
                        Text("No trade-in amount found for \(r.plan.carrier), - ask the carrier what your \(old.isEmpty ? "old phone" : old) gets.").font(.caption2)
                    }
                }
                if r.plan.freePhoneOffer {
                    Text("This carrier advertises a phone deal; its bill credits could lower the phone's cost, but only if you stay 24-36 months, and the amount isn't checked here.")
                        .font(.caption2)
                }
                Text("Not included: the phone, taxes, other fees, accessories.").font(.caption2)
            }
            if let url = URL(string: r.plan.signupUrl) { Link("View plan", destination: url) }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        // UIColor.secondarySystemBackground (not a flat gray-opacity overlay)
        // so result cards read correctly in both light and dark mode instead
        // of going murky/low-contrast once the system switches to dark.
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}
