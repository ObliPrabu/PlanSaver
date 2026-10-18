package com.obliprabu.plansaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlanSaverTheme { Surface(Modifier.fillMaxSize()) { PlanSaverApp(RemotePlanRepository(BuildConfig.DEALS_URL)) } } }
    }
}

private val dataChoices = listOf("1-5 GB" to 5, "10 GB" to 10, "25 GB" to 25, "Unlimited" to null)

@Composable
fun PlanSaverApp(repo: PlanRepository) {
    var lines by rememberSaveable { mutableIntStateOf(1) }
    var dataIdx by rememberSaveable { mutableIntStateOf(3) }
    var customLines by rememberSaveable { mutableStateOf(false) }
    var lineNeedsCsv by rememberSaveable { mutableStateOf("3,3,3,3,3,3") } // data choice index of each line
    val lineNeeds = lineNeedsCsv.split(",").map { it.toIntOrNull()?.coerceIn(dataChoices.indices) ?: 3 }
    var current by rememberSaveable { mutableStateOf("") }
    var postpaid by rememberSaveable { mutableStateOf(true) }
    var freePhone by rememberSaveable { mutableStateOf(false) }
    var brand by rememberSaveable { mutableStateOf("iPhone") }
    var familyIdx by rememberSaveable { mutableIntStateOf(3) }
    var variant by rememberSaveable { mutableStateOf("") }
    var familyPicked by rememberSaveable { mutableStateOf(false) }
    var otherPhones by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Result>?>(null) }
    var sortByData by rememberSaveable { mutableStateOf(false) }
    var searchedPhone by rememberSaveable { mutableStateOf<String?>(null) }
    // Results are not saveable, so remember only that the results screen was open and search again after process death.
    var resultsOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        loading = true
        val fams = Phones.brands.getValue(brand)
        val phone = if (freePhone) fams[familyIdx.coerceIn(fams.indices)].name(variant) else null
        scope.launch {
            val found = try {
                repo.search(SearchRequest(lines, dataChoices[dataIdx].second,
                    current.toDoubleOrNull() ?: 0.0, postpaid, freePhone, phone, otherPhones,
                    lineDataNeeds = if (customLines && lines > 1) (0 until lines).map { dataChoices[lineNeeds[it]].second } else null))
            } catch (e: Exception) {
                emptyList()
            }
            searchedPhone = phone
            results = found
            resultsOpen = true
            loading = false
        }
    }

    // After the process was killed with the results screen open: rebuild the results from the saved form.
    LaunchedEffect(Unit) { if (resultsOpen && results == null) runSearch() }

    // Two screens: the search form, and the results. Back from results returns to the form.
    BackHandler(enabled = resultsOpen) { results = null; resultsOpen = false }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        if (!resultsOpen) {
            Text("PlanSaver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp))
            Text("Find the cheapest US phone plan for what you actually use.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("How many lines?", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..6).forEach { n ->
                        FilterChip(selected = lines == n, onClick = { lines = n }, label = { Text("$n") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (lines > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = customLines, onCheckedChange = { customLines = it })
                    Text("Different data for each line")
                }
                if (customLines && lines > 1) {
                    (0 until lines).forEach { k ->
                        Text("Line ${k + 1}", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dataChoices.forEachIndexed { i, (label, _) ->
                                FilterChip(selected = lineNeeds[k] == i, onClick = {
                                    lineNeedsCsv = lineNeeds.toMutableList().also { it[k] = i }.joinToString(",")
                                }, label = { Text(label) })
                            }
                        }
                    }
                } else {
                    Text("Data per line", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dataChoices.forEachIndexed { i, (label, _) ->
                            FilterChip(selected = dataIdx == i, onClick = { dataIdx = i }, label = { Text(label) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = { v -> current = v.filter { it.isDigit() || it == '.' }.take(7) },
                    label = { Text("Your current phone bill (all lines, per month)") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Checkbox(checked = postpaid, onCheckedChange = { postpaid = it })
                    Text("Include big-carrier and cable plans (Verizon, AT&T, T-Mobile, Xfinity, Spectrum)")
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Checkbox(checked = freePhone, onCheckedChange = { freePhone = it })
                    Text("Only carriers offering a free or discounted phone")
                }

                if (freePhone) {
                    val families = Phones.brands.getValue(brand)
                    val family = families[familyIdx.coerceIn(families.indices)]
                    Spacer(Modifier.height(12.dp))
                    Text("Which phone do you want?", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Phones.brands.keys.forEach { br ->
                            FilterChip(selected = brand == br, onClick = {
                                brand = br
                                familyIdx = 0
                                variant = ""
                                familyPicked = false
                            }, label = { Text(br) })
                        }
                    }
                    // Once a model is picked, hide the other generations so it isn't confusing.
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!familyPicked) {
                            families.forEachIndexed { i, f ->
                                FilterChip(selected = familyIdx == i, onClick = {
                                    familyIdx = i
                                    familyPicked = true
                                    if (variant !in f.variants) variant = ""
                                }, label = { Text(f.label) })
                            }
                        } else {
                            FilterChip(selected = true, onClick = { familyPicked = false }, label = { Text(family.label) })
                            TextButton(onClick = { familyPicked = false }) { Text("Change") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        family.variants.forEach { v ->
                            FilterChip(selected = variant == v, onClick = { variant = v },
                                label = { Text(v.ifEmpty { "Base" }.trim()) })
                        }
                    }
                    Text("Selected: " + family.name(variant), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = otherPhones, onCheckedChange = { otherPhones = it })
                        Text("Check for better phones as well")
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Every tap runs a fresh search.
                Button(
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { runSearch() },
                ) { Text(if (loading) "Searching..." else "Search plans") }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            // Same idea as BuddyVerse's Welcome screen: small, faded, centered, read live from versionName.
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        } else {
            val list = results.orEmpty()
            val lineCount = lines
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = { results = null; resultsOpen = false }) { Text("< Back") }
                Text("Results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                searchedPhone?.let {
                    Text("Phone: $it. Carriers below advertise phone deals, but whether $it is covered " +
                        "varies by carrier and changes weekly - confirm on their site.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (lineCount > 4) Text("For more than 4 lines the 4-line price per line is used, so totals are estimates.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Sort:")
                    FilterChip(selected = !sortByData, onClick = { sortByData = false }, label = { Text("Lowest price") })
                    FilterChip(selected = sortByData, onClick = { sortByData = true }, label = { Text("Most data") })
                }
                if (results == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                else if (list.isEmpty()) Text("No plans matched. Try more data or include big carriers.")
                else list.sortedWith(compareBy<Result> { it.otherPhone }
                    .thenBy { if (sortByData) -(it.plan.dataGb ?: 1_000_000) else 0 }.thenBy { it.monthly })
                    .forEach { ResultCard(it, current.toDoubleOrNull() != null, lineCount) }
                Text("Prices researched Oct 2026 from third-party roundups, not checked with the carriers, and may have changed - confirm on the carrier's site. " +
                    "PlanSaver may earn a commission if you sign up through a link.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }
}

@Composable
private fun ResultCard(r: Result, showSavings: Boolean, lines: Int) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            if (r.assignments != null) {
                Text("${r.plan.carrier} - a plan for each line", fontWeight = FontWeight.Bold)
                r.assignments.forEach { a ->
                    Text(String.format(Locale.US, "Line %d: %s (%s) - $%.2f", a.line, a.plan.name,
                        a.plan.dataGb?.let { "$it GB" } ?: "Unlimited", a.price), style = MaterialTheme.typography.bodySmall)
                }
                Text("Check that ${r.plan.carrier} lets you mix plans on one account.", style = MaterialTheme.typography.labelSmall)
            } else {
            Text("${r.plan.carrier} - ${r.plan.name}", fontWeight = FontWeight.Bold)
            Text("${r.plan.dataGb?.let { "$it GB" } ?: "Unlimited"} - ${r.plan.network} network - ${if (r.plan.prepaid) "prepaid" else "postpaid"}",
                style = MaterialTheme.typography.bodySmall)
            if (r.plan.note.isNotEmpty()) Text(r.plan.note, style = MaterialTheme.typography.bodySmall)
            }
            r.plan.deal?.let {
                Text((if (r.liveDeal) "Live " else "") + (if (r.otherPhone) "better phone deal: " else if (r.plan.freePhoneOffer) "phone deal: " else "deal: ") + it +
                    (if (r.plan.dealDate.isNotEmpty()) " (as of ${r.plan.dealDate})" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (r.plan.freePhoneOffer) Text("Usually needs a trade-in and 24-36 months of bill credits - leaving early forfeits them.",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (r.plan.dealUrl.startsWith("https://")) TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.plan.dealUrl))) }) { Text("See the offer") }
            if (lines > 1 && r.plan.linesNote.isNotEmpty())
                Text("Multi-line price found online" + (if (r.plan.linesDate.isNotEmpty()) " (as of ${r.plan.linesDate})" else "") +
                    ", unverified: " + r.plan.linesNote, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            if (r.plan.extraNotes.isNotEmpty()) {
                Text("Found online for ${r.plan.carrier} (unverified; may differ by plan):", style = MaterialTheme.typography.labelSmall)
                r.plan.extraNotes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            coverageLinks(r.plan.network).forEach { (name, url) ->
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("$name coverage map") }
            }
            if (r.plan.perks.isNotEmpty()) Text("Perks: " + r.plan.perks.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            if (lines > 1 && r.assignments == null && lines !in r.plan.pricePerLine)
                Text("Estimate: no $lines-line price on file, so this uses the nearest known per-line price.",
                    style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "$%.2f / month", r.monthly), style = MaterialTheme.typography.titleMedium)
            if (r.plan.termMonths > 1 && r.assignments == null)
                Text(String.format(Locale.US, "Pay $%.0f up front for %d months", r.monthly * r.plan.termMonths, r.plan.termMonths),
                    style = MaterialTheme.typography.bodySmall)
            if (showSavings && r.yearlySavings > 0)
                Text(String.format(Locale.US, "Save about $%.0f a year", r.yearlySavings), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.plan.signupUrl))) }) { Text("View plan") }
        }
    }
}
