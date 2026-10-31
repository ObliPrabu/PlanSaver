package com.obliprabu.plansaver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Keeps the typed answers through rotation and process death, like the other forms in the app. */
private val CarPlanEntrySaver = listSaver<CarPlanEntry, Any>(
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
fun MyCarsForm() {
    var a by rememberSaveable(stateSaver = CarPlanEntrySaver) { mutableStateOf(CarPlanEntry()) }
    var b by rememberSaveable(stateSaver = CarPlanEntrySaver) { mutableStateOf(CarPlanEntry()) }
    CarForm("Vehicle A", a) { a = it }
    CarForm("Vehicle B", b) { b = it }
    Text("Side by side", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    CompareTable(a.vehicle.ifBlank { "Vehicle A" }, b.vehicle.ifBlank { "Vehicle B" }, compareMyCars(a, b))
    Text("These are your own numbers, not looked up. The yearly and total costs add only the costs you entered, so a blank answer " +
        "counts as nothing. Loan interest, lease fees, repairs, registration and depreciation are not included, and tires count as " +
        "one set unless you say how many years a set lasts.", style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun CarForm(title: String, e: CarPlanEntry, onChange: (CarPlanEntry) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AskText("What vehicle would you like to own first? (make, model and year)", e.vehicle) { onChange(e.copy(vehicle = it)) }
            AskNumber("What is the cost of the vehicle?", e.price, prefix = "$") { onChange(e.copy(price = it)) }
            AskChoice("How do you plan on paying for it?", PAY_OPTIONS, e.payHow) { onChange(e.copy(payHow = it)) }
            AskNumber("What are the miles per gallon (MPG) for this vehicle?", e.mpg, suffix = "MPG") { onChange(e.copy(mpg = it)) }
            AskNumber("How much is liability insurance?", e.liability, prefix = "$") { onChange(e.copy(liability = it)) }
            AskNumber("How much does full insurance coverage cost?", e.full, prefix = "$") { onChange(e.copy(full = it)) }
            AskChoice("How do you plan on paying for the insurance? (the two amounts above are for each payment)",
                INSURANCE_FREQUENCIES, e.insFreq) { onChange(e.copy(insFreq = it)) }
            AskNumber("How much does an oil change cost on average?", e.oilCost, prefix = "$") { onChange(e.copy(oilCost = it)) }
            AskNumber("How often do you need to change the oil? (every how many months)", e.oilMonths, suffix = "months") { onChange(e.copy(oilMonths = it)) }
            AskNumber("What is the cost for a set of tires (all four tires) on average?", e.tires, prefix = "$") { onChange(e.copy(tires = it)) }
            AskNumber("How many years does a set of tires last? (optional)", e.tireYears, suffix = "years") { onChange(e.copy(tireYears = it)) }
            AskNumber("What is the yearly fuel cost on average for this vehicle?", e.fuelYear, prefix = "$") { onChange(e.copy(fuelYear = it)) }
            AskNumber("Estimated life of the car? How long do you plan on having it? (years)", e.keepYears, suffix = "years") { onChange(e.copy(keepYears = it)) }
            AskText("Why did you pick this vehicle?", e.why) { onChange(e.copy(why = it)) }
            AskText("What type of safety features does it have to increase protection?", e.safety) { onChange(e.copy(safety = it)) }
        }
    }
}

@Composable
private fun AskText(question: String, value: String, onValue: (String) -> Unit) {
    Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    OutlinedTextField(value = value, onValueChange = { onValue(it.take(200)) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AskNumber(question: String, value: String, prefix: String? = null, suffix: String? = null, onValue: (String) -> Unit) {
    Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() || it == '.' }.take(9)) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AskChoice(question: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o -> FilterChip(selected = i == selected, onClick = { onPick(i) }, label = { Text(o) }) }
    }
}
