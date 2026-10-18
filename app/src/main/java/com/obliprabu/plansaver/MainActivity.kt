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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            PlanSaverTheme(themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    PlanSaverApp(RemotePlanRepository(BuildConfig.DEALS_URL), themeMode) { themeMode = it }
                }
            }
        }
    }
}

private val dataChoices = listOf("1-5 GB" to 5, "10 GB" to 10, "25 GB" to 25, "Unlimited" to null)

@Composable
fun PlanSaverApp(repo: PlanRepository, themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    var lines by rememberSaveable { mutableIntStateOf(1) }
    var dataIdx by rememberSaveable { mutableIntStateOf(3) }
    var customLines by rememberSaveable { mutableStateOf(false) }
    var lineNeedsCsv by rememberSaveable { mutableStateOf("3,3,3,3,3,3") } // data choice index of each line
    val lineNeeds = lineNeedsCsv.split(",").map { it.toIntOrNull()?.coerceIn(dataChoices.indices) ?: 3 }
    var current by rememberSaveable { mutableStateOf("") }
    var postpaid by rememberSaveable { mutableStateOf(true) }
    var freePhone by rememberSaveable { mutableStateOf(false) }
    var brand by rememberSaveable { mutableStateOf("iPhone") }
    var familyIdx by rememberSaveable { mutableIntStateOf(Phones.defaultIphoneIdx) }
    var variant by rememberSaveable { mutableStateOf("") }
    var familyPicked by rememberSaveable { mutableStateOf(true) } // starts on the default iPhone 16, shown as one chip + "Change"
    var otherPhones by rememberSaveable { mutableStateOf(false) }
    var quizOpen by rememberSaveable { mutableStateOf(false) }
    var quizUsage by rememberSaveable { mutableIntStateOf(-1) } // -1 = not answered yet
    var quizWifi by rememberSaveable { mutableStateOf(true) }
    var quizHotspot by rememberSaveable { mutableStateOf(false) }
    var tradeIn by rememberSaveable { mutableStateOf(false) }
    var oldBrand by rememberSaveable { mutableStateOf("iPhone") }
    var oldFamilyIdx by rememberSaveable { mutableIntStateOf(Phones.defaultIphoneIdx) }
    var oldVariant by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Result>?>(null) }
    var sortByData by rememberSaveable { mutableStateOf(false) }
    var filterNetworksCsv by rememberSaveable { mutableStateOf("") }
    var filterType by rememberSaveable { mutableIntStateOf(0) } // 0 any, 1 prepaid, 2 postpaid
    var filterDeal by rememberSaveable { mutableStateOf(false) }
    var filterUnlimited by rememberSaveable { mutableStateOf(false) }
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

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        if (!resultsOpen) {
            Text("PlanSaver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, end = 72.dp))
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
                if (lines > 1) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = customLines, role = Role.Checkbox, onValueChange = { customLines = it })) {
                    Checkbox(checked = customLines, onCheckedChange = null)
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
                    TextButton(onClick = { quizOpen = !quizOpen }) { Text(if (quizOpen) "Hide the quiz" else "Not sure? Answer 3 quick questions") }
                    if (quizOpen) {
                        fun applyQuiz() { if (quizUsage >= 0) dataIdx = quizDataIndex(quizUsage, quizWifi, quizHotspot) }
                        Text("What do you do most when you're not on Wi-Fi?", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Texts and reading", "Music and social", "Streaming video").forEachIndexed { i, label ->
                                FilterChip(selected = quizUsage == i, onClick = { quizUsage = i; applyQuiz() }, label = { Text(label) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = quizWifi, role = Role.Checkbox, onValueChange = { quizWifi = it; applyQuiz() })) {
                            Checkbox(checked = quizWifi, onCheckedChange = null)
                            Text("I have Wi-Fi at home")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = quizHotspot, role = Role.Checkbox, onValueChange = { quizHotspot = it; applyQuiz() })) {
                            Checkbox(checked = quizHotspot, onCheckedChange = null)
                            Text("I share my data as a hotspot")
                        }
                        Text(if (quizUsage < 0) "Pick what you do most and I'll choose the data for you."
                            else "A rough guess: ${dataChoices[dataIdx].first} per line. You can still change it above.",
                            style = MaterialTheme.typography.bodySmall)
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = postpaid, role = Role.Checkbox, onValueChange = { postpaid = it }).padding(vertical = 8.dp)) {
                    Checkbox(checked = postpaid, onCheckedChange = null)
                    Text("Include big-carrier and cable plans (Verizon, AT&T, T-Mobile, Xfinity, Spectrum)")
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = freePhone, role = Role.Checkbox, onValueChange = { freePhone = it }).padding(bottom = 8.dp)) {
                    Checkbox(checked = freePhone, onCheckedChange = null)
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
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        family.variants.forEach { v ->
                            FilterChip(selected = variant == v, onClick = { variant = v },
                                label = { Text(v.ifEmpty { "Base" }.trim()) })
                        }
                    }
                    Text("Selected: " + family.name(variant), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = otherPhones, role = Role.Checkbox, onValueChange = { otherPhones = it })) {
                        Checkbox(checked = otherPhones, onCheckedChange = null)
                        Text("Check for better phones as well")
                    }
                // A trade-in only comes with buying a new phone, so it is only offered here.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.toggleable(value = tradeIn, role = Role.Checkbox, onValueChange = { tradeIn = it })) {
                    Checkbox(checked = tradeIn, onCheckedChange = null)
                    Text("I'll trade in my old phone")
                }
                if (tradeIn) {
                    val oldFamilies = Phones.brands.getValue(oldBrand)
                    val oldFamily = oldFamilies[oldFamilyIdx.coerceIn(oldFamilies.indices)]
                    Text("Which phone are you trading in?", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Phones.brands.keys.forEach { br ->
                            FilterChip(selected = oldBrand == br, onClick = { oldBrand = br; oldFamilyIdx = 0; oldVariant = "" },
                                label = { Text(br) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        oldFamilies.forEachIndexed { i, f ->
                            FilterChip(selected = oldFamilyIdx == i, onClick = {
                                oldFamilyIdx = i
                                if (oldVariant !in f.variants) oldVariant = ""
                            }, label = { Text(f.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        oldFamily.variants.forEach { v ->
                            FilterChip(selected = oldVariant == v, onClick = { oldVariant = v },
                                label = { Text(v.ifEmpty { "Base" }.trim()) })
                        }
                    }
                    Text("Trading in: " + oldFamily.name(oldVariant), style = MaterialTheme.typography.bodySmall)
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
            val all = results.orEmpty()
            val filterNetworks = filterNetworksCsv.split(",").filter { it.isNotEmpty() }.toSet()
            val list = applyFilters(all, ResultFilters(filterNetworks, filterType, filterDeal, filterUnlimited))
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically, ) {
                    Text("Filter:")
                    listOf("Verizon", "AT&T", "T-Mobile").forEach { n ->
                        FilterChip(selected = n in filterNetworks, onClick = {
                            filterNetworksCsv = (if (n in filterNetworks) filterNetworks - n else filterNetworks + n).joinToString(",")
                        }, label = { Text(n) })
                    }
                    FilterChip(selected = filterType == 1, onClick = { filterType = if (filterType == 1) 0 else 1 }, label = { Text("Prepaid") })
                    FilterChip(selected = filterType == 2, onClick = { filterType = if (filterType == 2) 0 else 2 }, label = { Text("Postpaid") })
                    FilterChip(selected = filterDeal, onClick = { filterDeal = !filterDeal }, label = { Text("Has a deal") })
                    FilterChip(selected = filterUnlimited, onClick = { filterUnlimited = !filterUnlimited }, label = { Text("Unlimited only") })
                }
                if (all.isNotEmpty()) Text("Showing ${list.size} of ${all.size} plans", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp))
                if (results == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                else if (all.isNotEmpty() && list.isEmpty()) Text("No plans match these filters. Turn one off to see more.")
                else if (list.isEmpty()) Text("No plans matched. Try more data or include big carriers.")
                else {
                    val picks = topPicks(list)
                    if (picks.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Top picks", fontWeight = FontWeight.Bold)
                            picks.forEach {
                                Text(String.format(Locale.US, "%s: %s - %s ($%.2f / month)", it.label, it.result.plan.carrier,
                                    it.result.plan.name, it.result.monthly), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (results != null && list.isNotEmpty()) list.sortedWith(compareBy<Result> { it.otherPhone }
                    .thenBy { if (sortByData) -(it.plan.dataGb ?: 1_000_000) else 0 }.thenBy { it.monthly })
                    .forEach {
                        ResultCard(it, current.toDoubleOrNull() != null, lineCount, freePhone && tradeIn,
                            Phones.brands.getValue(oldBrand).let { it[oldFamilyIdx.coerceIn(it.indices)].name(oldVariant) })
                    }
                Text("Prices researched Oct 2026 from third-party roundups, not checked with the carriers, and may have changed - confirm on the carrier's site. " +
                    "PlanSaver may earn a commission if you sign up through a link.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }

    // Dark mode toggle, top right - same corner/pill style as the BuddyVerse
    // app's toggle, floating over the content instead of inline so it stays
    // put while the form scrolls or the results screen is open. Cycles
    // Auto -> Light -> Dark -> Auto (see Theme.kt's ThemeMode doc comment for
    // why a manual override exists at all).
    val (themeEmoji, themeLabel) = when (themeMode) {
        ThemeMode.SYSTEM -> "🌓" to "Auto"
        ThemeMode.LIGHT -> "☀️" to "Light"
        ThemeMode.DARK -> "🌙" to "Dark"
    }
    AssistChip(
        onClick = {
            onThemeModeChange(when (themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            })
        },
        label = { Text("$themeEmoji $themeLabel") },
        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
    )
    }
}

@Composable
private fun ResultCard(r: Result, showSavings: Boolean, lines: Int, tradeIn: Boolean, oldPhone: String) {
    val ctx = LocalContext.current
    var showTotal by rememberSaveable { mutableStateOf(false) }
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
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.dealColor)
                if (r.plan.freePhoneOffer) Text("Usually needs a trade-in and 24-36 months of bill credits - leaving early forfeits them.",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (r.plan.dealUrl.startsWith("https://")) TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.plan.dealUrl))) }) { Text("See the offer") }
            if (lines > 1 && r.plan.linesNote.isNotEmpty())
                Text("Multi-line price found online" + (if (r.plan.linesDate.isNotEmpty()) " (as of ${r.plan.linesDate})" else "") +
                    ", unverified: " + r.plan.linesNote, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.dealColor)
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
            TextButton(onClick = { showTotal = !showTotal }) { Text(if (showTotal) "Hide my total cost" else "How much will I pay in total?") }
            if (showTotal) {
                val activation = (r.plan.activationFee ?: 0.0) * lines
                listOf(12, 24, 36).forEach { m ->
                    val c = totalCost(r.monthly, m, 0.0, 0.0, activation)
                    Text(String.format(Locale.US, "%d months: plan $%.0f", m, c.plan) +
                        (if (activation > 0) String.format(Locale.US, " + activation $%.0f", activation) else "") +
                        String.format(Locale.US, " = $%.0f", c.total), style = MaterialTheme.typography.bodySmall)
                }
                Text(r.plan.activationFee?.let {
                    String.format(Locale.US, "Activation fee found online: $%.0f per line, so $%.0f for %d line%s (unverified).", it, activation, lines, if (lines == 1) "" else "s")
                } ?: "No activation fee found for ${r.plan.carrier} yet, so none is counted - ask the carrier.", style = MaterialTheme.typography.labelSmall)
                Text("The phone itself is not counted here.", style = MaterialTheme.typography.labelSmall)
                if (tradeIn) Text(r.plan.tradeInMax?.let {
                    String.format(Locale.US, "Trade-in%s: this carrier's own text says up to $%.0f. That is a best case; what your phone gets depends on its model and condition, so confirm with the carrier.",
                        if (oldPhone.isNotEmpty()) " of $oldPhone" else "", it)
                } ?: "No trade-in amount found for ${r.plan.carrier} - ask the carrier what your ${oldPhone.ifEmpty { "old phone" }} gets.",
                    style = MaterialTheme.typography.labelSmall)
                if (r.plan.freePhoneOffer) Text("This carrier advertises a phone deal; its bill credits could lower the phone's cost, " +
                    "but only if you stay 24-36 months, and the amount isn't checked here.", style = MaterialTheme.typography.labelSmall)
                Text("Not included: the phone, taxes, other fees, accessories.", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.plan.signupUrl))) }) { Text("View plan") }
        }
    }
}
