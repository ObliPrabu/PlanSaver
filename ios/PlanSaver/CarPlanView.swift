import SwiftUI

/// The "My own questions" half of the Cars tab: two vehicles' worth of questions, then the side-by-side comparison
/// (see CarPlan.swift). Mirrors Android's CarPlanScreen.kt.
struct CarPlanForm: View {
    // Owned by CarsView so the answers survive switching to Catalog cars and back.
    @Binding var a: CarPlanEntry
    @Binding var b: CarPlanEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CarForm(title: "Vehicle A", entry: $a)
            CarForm(title: "Vehicle B", entry: $b)
            Text("Side by side").font(.headline)
            CompareTable(leftName: a.vehicle.isEmpty ? "Vehicle A" : a.vehicle, rightName: b.vehicle.isEmpty ? "Vehicle B" : b.vehicle,
                         rows: compareMyCars(a, b))
            Text("These are your own numbers, not looked up. The yearly and total costs add only the costs you entered, so a blank answer counts as nothing. Loan interest, lease fees, repairs, registration and depreciation are not included, and tires count as one set unless you say how many years a set lasts.")
                .font(.footnote).padding(.vertical, 8)
        }
    }
}

private struct CarForm: View {
    let title: String
    @Binding var entry: CarPlanEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.title3).bold()
            AskText(title: title, question: "What vehicle would you like to own first? (make, model and year)", text: $entry.vehicle)
            AskNumber(title: title, question: "What is the cost of the vehicle?", text: $entry.price, prefix: "$")
            AskChoice(title: title, question: "How do you plan on paying for it?", options: payOptions, selected: $entry.payHow)
            AskNumber(title: title, question: "What are the miles per gallon (MPG) for this vehicle?", text: $entry.mpg, suffix: "MPG")
            AskNumber(title: title, question: "How much is liability insurance?", text: $entry.liability, prefix: "$")
            AskNumber(title: title, question: "How much does full insurance coverage cost?", text: $entry.full, prefix: "$")
            AskChoice(title: title, question: "How do you plan on paying for the insurance? (the two amounts above are for each payment)",
                      options: insuranceFrequencies, selected: $entry.insFreq)
            AskNumber(title: title, question: "How much does an oil change cost on average?", text: $entry.oilCost, prefix: "$")
            AskNumber(title: title, question: "How often do you need to change the oil? (every how many months)", text: $entry.oilMonths, suffix: "months")
            AskNumber(title: title, question: "What is the cost for a set of tires (all four tires) on average?", text: $entry.tires, prefix: "$")
            AskNumber(title: title, question: "How many years does a set of tires last? (optional)", text: $entry.tireYears, suffix: "years")
            AskNumber(title: title, question: "What is the yearly fuel cost on average for this vehicle?", text: $entry.fuelYear, prefix: "$")
            AskNumber(title: title, question: "Estimated life of the car? How long do you plan on having it? (years)", text: $entry.keepYears, suffix: "years")
            AskText(title: title, question: "Why did you pick this vehicle?", text: $entry.why)
            AskText(title: title, question: "What type of safety features does it have to increase protection?", text: $entry.safety)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}

private struct AskText: View {
    let title: String
    let question: String
    @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(question).padding(.top, 8)
            TextField("", text: $text)
                .padding(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.5)))
                .accessibilityLabel("\(title), \(question)")
                .onChange(of: text) { v in if v.count > 200 { text = String(v.prefix(200)) } }
        }
    }
}

private struct AskNumber: View {
    let title: String
    let question: String
    @Binding var text: String
    var prefix: String? = nil
    var suffix: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(question).padding(.top, 8)
            HStack {
                if let prefix = prefix { Text(prefix) }
                TextField("", text: $text)
                    .keyboardType(.decimalPad)
                    .accessibilityLabel("\(title), \(question)")
                    .onChange(of: text) { v in
                        let f = cleanNumberInput(v, maxLen: 9)
                        if f != v { text = f }
                    }
                if let suffix = suffix { Text(suffix) }
            }
            .padding(8)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.5)))
        }
    }
}

private struct AskChoice: View {
    let title: String
    let question: String
    let options: [String]
    @Binding var selected: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(question).padding(.top, 8)
            Picker("\(title), \(question)", selection: $selected) {
                ForEach(0..<options.count, id: \.self) { Text(options[$0]).tag($0) }
            }.pickerStyle(SegmentedPickerStyle())
        }
    }
}
