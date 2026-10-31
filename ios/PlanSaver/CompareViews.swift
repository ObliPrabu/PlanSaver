import SwiftUI

/// Content never gets wider than this, so text and cards stay readable on a big screen (an iPad).
let contentMaxWidth: CGFloat = 720

/// Four tabs around the app; the dark-mode toggle floats over all of them (same pill as Android's ThemeChip).
struct RootView: View {
    @State private var themeMode: ThemeMode = .system
    @State private var catalog: Catalog? = nil

    var body: some View {
        ZStack(alignment: .topTrailing) {
            TabView {
                ContentView().tabItem { Label("Plans", systemImage: "antenna.radiowaves.left.and.right") }
                InsuranceView(catalog: catalog, onSearch: { catalog = nil; CatalogRepository.load { catalog = $0 } }).tabItem { Label("Insurance", systemImage: "shield") }
                PhonesView(catalog: catalog).tabItem { Label("Phones", systemImage: "iphone") }
                CarsView(catalog: catalog).tabItem { Label("Cars", systemImage: "car") }
            }
            Button(action: {
                themeMode = themeMode == .system ? .light : (themeMode == .light ? .dark : .system)
            }) {
                Text(themeMode.toggleLabel)
                    .font(.footnote.bold())
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Color(UIColor.secondarySystemBackground))
                    .clipShape(Capsule())
            }
            .padding(12)
        }
        .preferredColorScheme(themeMode.colorScheme)
        .onAppear { CatalogRepository.load { catalog = $0 } }
    }
}

private struct Frame<Content: View>: View {
    let title: String
    let subtitle: String
    let content: Content
    init(_ title: String, _ subtitle: String, @ViewBuilder content: () -> Content) {
        self.title = title; self.subtitle = subtitle; self.content = content()
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(title).font(.largeTitle).bold().padding(.trailing, 80)
                Text(subtitle).font(.subheadline)
                content
            }
            .padding(16)
            .frame(maxWidth: contentMaxWidth) // readable width on an iPad; iPhones are narrower, so nothing changes
            .frame(maxWidth: .infinity)
        }
    }
}

private struct StatusLine: View {
    let catalog: Catalog?
    let empty: Bool
    let what: String
    var body: some View {
        if catalog == nil { ProgressView() }
        else if empty { Text("No \(what) in the catalog yet. The robot adds them as it runs.") }
    }
}

struct InsuranceView: View {
    let catalog: Catalog?
    let onSearch: () -> Void
    @State private var full = true
    @State private var current = ""
    // Results show what was searched, so changing the form does nothing until Search is tapped again.
    @State private var searched = false
    @State private var searchedFull = true
    @State private var searchedCurrent = ""

    var body: some View {
        Frame("Car insurance", "Average monthly costs for full coverage or the state minimum.") {
            Picker("Coverage", selection: $full) {
                Text("Full coverage").tag(true)
                Text("Minimum").tag(false)
            }.pickerStyle(SegmentedPickerStyle())
            HStack {
                Text("$")
                TextField("What you pay now (per month, optional)", text: $current)
                    .keyboardType(.decimalPad)
                    .onChange(of: current) { v in
                        let f = String(v.filter { $0.isNumber || $0 == "." }.prefix(7))
                        if f != v { current = f }
                    }
            }
            .padding(8)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.5)))
            Text("These are averages found by the robot on insurance-review sites, not quotes. Your own rate depends on your age, driving record, car and state, so get a real quote before deciding.")
                .font(.footnote)
            Button(action: { searchedFull = full; searchedCurrent = current; searched = true; onSearch() }) {
                Text(searched && catalog == nil ? "Searching..." : "Search insurance")
                    .frame(maxWidth: .infinity).padding().background(searched && catalog == nil ? Color.gray : Color.blue)
                    .foregroundColor(.white).cornerRadius(10)
            }.disabled(searched && catalog == nil)
            if searched {
                StatusLine(catalog: catalog, empty: catalog?.insurers.isEmpty == true, what: "insurers")
                if let ins = catalog?.insurers, !ins.isEmpty {
                    Text("Average monthly cost, \(searchedFull ? "full coverage" : "minimum coverage"):").font(.headline)
                }
                ForEach(sortInsurers(catalog?.insurers ?? [], full: searchedFull)) { ins in card(ins) }
                Text("Figures come from different studies and dates, so a few dollars of difference between insurers means little.")
                    .font(.footnote).padding(.vertical, 8)
            }
        }
    }

    private func card(_ ins: CatalogInsurer) -> some View {
        let monthly = ins.monthly(full: searchedFull)
        let field = searchedFull ? "fullMonthly" : "minMonthly"
        return VStack(alignment: .leading, spacing: 4) {
            Text(ins.name).bold()
            if let m = monthly {
                Text(String(format: "$%.0f / month (about $%.0f a year)", m, m * 12)).font(.headline)
                if let src = sourceDomain(ins.sources[field]) {
                    Text("Source: \(src)" + (ins.updated.isEmpty ? "" : " (as of \(ins.updated))")).font(.caption2)
                }
                if let now = Double(searchedCurrent), m < now {
                    Text(String(format: "About $%.0f a year less than you pay now", (now - m) * 12)).foregroundColor(.blue)
                }
            } else {
                Text("No \(searchedFull ? "full-coverage" : "minimum-coverage") average found yet.").font(.footnote)
            }
            if ins.url.hasPrefix("https://"), let url = URL(string: ins.url) { Link("Get a real quote", destination: url) }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}

struct CompareTable: View {
    let leftName: String
    let rightName: String
    let rows: [CompareRow]
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Spacer().frame(maxWidth: .infinity)
                Text(leftName).bold().frame(maxWidth: .infinity, alignment: .leading)
                Text(rightName).bold().frame(maxWidth: .infinity, alignment: .leading)
            }
            ForEach(0..<rows.count, id: \.self) { i in
                Divider()
                HStack(alignment: .top) {
                    Text(rows[i].label).font(.footnote).frame(maxWidth: .infinity, alignment: .leading)
                    Text(rows[i].left + (rows[i].better == 0 ? "  ✓" : ""))
                        .foregroundColor(rows[i].better == 0 ? .blue : .primary).frame(maxWidth: .infinity, alignment: .leading)
                    Text(rows[i].right + (rows[i].better == 1 ? "  ✓" : ""))
                        .foregroundColor(rows[i].better == 1 ? .blue : .primary).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(12)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}

struct PhonesView: View {
    let catalog: Catalog?
    @State private var a = 0
    @State private var b = 1

    var body: some View {
        let phones = catalog?.phones ?? []
        Frame("Compare phones", "Pick two phones to see them side by side.") {
            StatusLine(catalog: catalog, empty: phones.isEmpty, what: "phones")
            if phones.count >= 2 {
                let left = phones[min(a, phones.count - 1)], right = phones[min(b, phones.count - 1)]
                Picker("Phone A", selection: $a) { ForEach(0..<phones.count, id: \.self) { Text(phones[$0].name).tag($0) } }
                    .pickerStyle(MenuPickerStyle())
                Picker("Phone B", selection: $b) { ForEach(0..<phones.count, id: \.self) { Text(phones[$0].name).tag($0) } }
                    .pickerStyle(MenuPickerStyle())
                CompareTable(leftName: left.name, rightName: right.name, rows: comparePhones(left, right))
                ForEach([left, right]) { p in
                    if p.url.hasPrefix("https://"), let url = URL(string: p.url) { Link("\(p.name) page", destination: url) }
                }
                Text("Found by the robot on the makers' pages and spec sites; \"not found yet\" means it has not located that figure. Prices are starting prices and may differ by storage, carrier and sales.")
                    .font(.footnote).padding(.vertical, 8)
            }
        }
    }
}

struct CarsView: View {
    let catalog: Catalog?
    @State private var a = 0
    @State private var b = 1
    @State private var mode = 0 // 0 = catalog cars, 1 = "My own questions" (CarPlanView.swift)

    var body: some View {
        let cars = catalog?.cars ?? []
        Frame("Compare cars", mode == 0 ? "Pick two cars to see them side by side." : "Answer the questions for two vehicles to compare them.") {
            Picker("Cars", selection: $mode) {
                Text("Catalog cars").tag(0)
                Text("My own questions").tag(1)
            }.pickerStyle(SegmentedPickerStyle())
            if mode == 1 { CarPlanForm() }
            if mode == 0 { StatusLine(catalog: catalog, empty: cars.isEmpty, what: "cars") }
            if mode == 0 && cars.count >= 2 {
                let left = cars[min(a, cars.count - 1)], right = cars[min(b, cars.count - 1)]
                Picker("Car A", selection: $a) { ForEach(0..<cars.count, id: \.self) { Text(cars[$0].name).tag($0) } }
                    .pickerStyle(MenuPickerStyle())
                Picker("Car B", selection: $b) { ForEach(0..<cars.count, id: \.self) { Text(cars[$0].name).tag($0) } }
                    .pickerStyle(MenuPickerStyle())
                CompareTable(leftName: left.name, rightName: right.name, rows: compareCars(left, right))
                ForEach([left, right]) { c in
                    if c.url.hasPrefix("https://"), let url = URL(string: c.url) { Link("\(c.name) page", destination: url) }
                }
                Text("Fuel economy is the US government's official EPA figure for one trim of each model (electric cars show MPGe, which is not the same as gas MPG). Prices are found by the robot when the maker's page shows one.")
                    .font(.footnote).padding(.vertical, 8)
            }
        }
    }
}
