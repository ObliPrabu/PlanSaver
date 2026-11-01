package com.obliprabu.plansaver

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Keeps the typed answers through rotation and process death, like the other forms in the app. */
val CarPlanEntrySaver = listSaver<CarPlanEntry, Any>(
    save = {
        listOf(it.vehicle, it.price, it.payHow, it.mpg, it.liability, it.full, it.insFreq, it.oilCost, it.oilMonths,
            it.tires, it.tireYears, it.fuelYear, it.keepYears, it.why, it.safety)
    },
    restore = {
        CarPlanEntry(it[0] as String, it[1] as String, it[2] as Int, it[3] as String, it[4] as String, it[5] as String,
            it[6] as Int, it[7] as String, it[8] as String, it[9] as String, it[10] as String, it[11] as String,
            it[12] as String, it[13] as String, it[14] as String)
    },
)

/** The "My own questions" half of the Cars tab: two vehicles' worth of questions, then the side-by-side comparison (see CarPlan.kt). */
@Composable
fun MyCarsForm(a: CarPlanEntry, onA: (CarPlanEntry) -> Unit, b: CarPlanEntry, onB: (CarPlanEntry) -> Unit) {
    // The two entries live in CarsScreen, so flipping to Catalog cars and back keeps every answer.
    CarForm("Vehicle A", a, onA)
    CarForm("Vehicle B", b, onB)
    Text("Side by side", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    CompareTable(a.vehicle.ifBlank { "Vehicle A" }, b.vehicle.ifBlank { "Vehicle B" }, compareMyCars(a, b))
    Text("These are your own numbers, not looked up. The yearly and total costs add only the costs you entered, so a blank answer " +
        "counts as nothing; when the two vehicles have not entered the same costs, the totals are marked (partial) and no side is ticked. Loan interest, lease fees, repairs, registration and depreciation are not included, and tires count as " +
        "one set unless you say how many years a set lasts.", style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun CarForm(title: String, e: CarPlanEntry, onChange: (CarPlanEntry) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AskText(title, "What vehicle would you like to own first? (make, model and year)", e.vehicle) { onChange(e.copy(vehicle = it)) }
            AskNumber(title, "What is the cost of the vehicle?", e.price, prefix = "$") { onChange(e.copy(price = it)) }
            AskChoice(title, "How do you plan on paying for it?", PAY_OPTIONS, e.payHow) { onChange(e.copy(payHow = it)) }
            AskNumber(title, "What are the miles per gallon (MPG) for this vehicle?", e.mpg, suffix = "MPG") { onChange(e.copy(mpg = it)) }
            AskNumber(title, "How much is liability insurance?", e.liability, prefix = "$") { onChange(e.copy(liability = it)) }
            AskNumber(title, "How much does full insurance coverage cost?", e.full, prefix = "$") { onChange(e.copy(full = it)) }
            AskChoice(title, "How do you plan on paying for the insurance? (the two amounts above are for each payment)",
                INSURANCE_FREQUENCIES, e.insFreq) { onChange(e.copy(insFreq = it)) }
            AskNumber(title, "How much does an oil change cost on average?", e.oilCost, prefix = "$") { onChange(e.copy(oilCost = it)) }
            AskNumber(title, "How often do you need to change the oil? (every how many months)", e.oilMonths, suffix = "months") { onChange(e.copy(oilMonths = it)) }
            AskNumber(title, "What is the cost for a set of tires (all four tires) on average?", e.tires, prefix = "$") { onChange(e.copy(tires = it)) }
            AskNumber(title, "How many years does a set of tires last? (optional)", e.tireYears, suffix = "years") { onChange(e.copy(tireYears = it)) }
            AskNumber(title, "What is the yearly fuel cost on average for this vehicle?", e.fuelYear, prefix = "$") { onChange(e.copy(fuelYear = it)) }
            AskNumber(title, "Estimated life of the car? How long do you plan on having it? (years)", e.keepYears, suffix = "years") { onChange(e.copy(keepYears = it)) }
            AskText(title, "Why did you pick this vehicle?", e.why) { onChange(e.copy(why = it)) }
            AskText(title, "What type of safety features does it have to increase protection?", e.safety) { onChange(e.copy(safety = it)) }
        }
    }
}

@Composable
private fun AskText(title: String, question: String, value: String, onValue: (String) -> Unit) {
    Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    OutlinedTextField(value = value, onValueChange = { onValue(it.take(200)) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title: $question" })
}

@Composable
private fun AskNumber(title: String, question: String, value: String, prefix: String? = null, suffix: String? = null, onValue: (String) -> Unit) {
    Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(cleanNumberInput(v, 9)) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title: $question" },
    )
}

@Composable
private fun AskChoice(title: String, question: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = "$title: $question" }) {
        options.forEachIndexed { i, o -> FilterChip(selected = i == selected, onClick = { onPick(i) }, label = { Text(o) }) }
    }
}
