package com.obliprabu.plansaver

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

private val tabs = listOf("Plans" to "📶", "Insurance" to "🛡️", "Phones" to "📱", "Cars" to "🚗")

/** Bottom tab bar around the four sections; each tab keeps its own form state while you visit another one. */
@Composable
fun AppShell(repo: PlanRepository, catalogRepo: CatalogRepository, themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    var catalogKey by remember { mutableIntStateOf(0) } // bumped by the Insurance search button to download the catalog again
    val catalog by produceState<Catalog?>(null, catalogKey) { value = null; value = catalogRepo.load() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> PlanSaverApp(repo)
                    1 -> InsuranceScreen(catalog) { catalogKey++ }
                    2 -> PhonesScreen(catalog)
                    else -> CarsScreen(catalog)
                }
            }
            ThemeChip(themeMode, onThemeModeChange, Modifier.align(Alignment.TopEnd))
        }
        NavigationBar {
            tabs.forEachIndexed { i, (label, emoji) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(emoji) }, label = { Text(label) })
            }
        }
    }
}

/** Dark mode toggle, top right on every tab: Auto -> Light -> Dark -> Auto (see Theme.kt's ThemeMode for why it exists). */
@Composable
fun ThemeChip(themeMode: ThemeMode, onChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val (emoji, label) = when (themeMode) {
        ThemeMode.SYSTEM -> "🌓" to "Auto"
        ThemeMode.LIGHT -> "☀️" to "Light"
        ThemeMode.DARK -> "🌙" to "Dark"
    }
    AssistChip(
        onClick = {
            onChange(when (themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            })
        },
        label = { Text("$emoji $label") },
        modifier = modifier.statusBarsPadding().padding(12.dp),
    )
}

@Composable
private fun ScreenFrame(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, end = 72.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
private fun Loading(catalog: Catalog?, empty: Boolean, what: String) {
    if (catalog == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
    else if (empty) Text("No $what in the catalog yet. The robot adds them as it runs.")
}

private fun open(ctx: android.content.Context, url: String) {
    if (url.startsWith("https://")) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

// ------------------------------------------------------------------ insurance

@Composable
fun InsuranceScreen(catalog: Catalog?, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    var full by rememberSaveable { mutableStateOf(true) }
    var current by rememberSaveable { mutableStateOf("") }
    // Results show what was searched, so changing the form does nothing until Search is tapped again.
    var searched by rememberSaveable { mutableStateOf(false) }
    var searchedFull by rememberSaveable { mutableStateOf(true) }
    var searchedCurrent by rememberSaveable { mutableStateOf("") }
    ScreenFrame("Car insurance", "Average monthly costs for full coverage or the state minimum.") {
        Text("Coverage", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = full, onClick = { full = true }, label = { Text("Full coverage") })
            FilterChip(selected = !full, onClick = { full = false }, label = { Text("Minimum (liability)") })
        }
        OutlinedTextField(
            value = current,
            onValueChange = { v -> current = v.filter { it.isDigit() || it == '.' }.take(7) },
            label = { Text("What you pay now (per month, optional)") },
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Text("These are averages found by the robot on insurance-review sites, not quotes. Your own rate depends on your age, " +
            "driving record, car and state, so get a real quote before deciding.", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = !(searched && catalog == null), onClick = {
            searchedFull = full; searchedCurrent = current; searched = true; onSearch()
        }) { Text(if (searched && catalog == null) "Searching..." else "Search insurance") }
        if (!searched) return@ScreenFrame
        Spacer(Modifier.height(12.dp))
        Loading(catalog, catalog?.insurers?.isEmpty() == true, "insurers")
        val now = searchedCurrent.toDoubleOrNull()
        val coverage = if (searchedFull) "full coverage" else "minimum coverage"
        if (catalog != null && catalog.insurers.isNotEmpty()) Text("Average monthly cost, $coverage:", fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp))
        sortInsurers(catalog?.insurers.orEmpty(), searchedFull).forEach { ins ->
            val monthly = ins.monthly(searchedFull)
            val field = if (searchedFull) "fullMonthly" else "minMonthly"
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(ins.name, fontWeight = FontWeight.Bold)
                    if (monthly == null) Text("No ${if (searchedFull) "full-coverage" else "minimum-coverage"} average found yet.",
                        style = MaterialTheme.typography.bodySmall)
                    else {
                        Text(String.format(Locale.US, "$%.0f / month (about $%.0f a year)", monthly, monthly * 12),
                            style = MaterialTheme.typography.titleMedium)
                        sourceDomain(ins.sources[field])?.let {
                            Text("Source: $it" + if (ins.updated.isNotEmpty()) " (as of ${ins.updated})" else "",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        if (now != null && monthly < now) Text(String.format(Locale.US, "About $%.0f a year less than you pay now", (now - monthly) * 12),
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (ins.url.startsWith("https://")) TextButton(onClick = { open(ctx, ins.url) }) { Text("Get a real quote") }
                }
            }
        }
        Text("Figures come from different studies and dates, so a few dollars of difference between insurers means little.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
    }
}

// ------------------------------------------------------------------ comparisons

@Composable
private fun <T> Picker(label: String, items: List<T>, selected: Int, name: (T) -> String, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: " + items.getOrNull(selected)?.let(name).orEmpty())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, item -> DropdownMenuItem(text = { Text(name(item)) }, onClick = { onPick(i); open = false }) }
        }
    }
}

@Composable
private fun CompareTable(leftName: String, rightName: String, rows: List<CompareRow>) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row { Spacer(Modifier.weight(1f)); Text(leftName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(rightName, Modifier.weight(1f), fontWeight = FontWeight.Bold) }
            rows.forEach { r ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(r.left + if (r.better == 0) "  ✓" else "", Modifier.weight(1f),
                        color = if (r.better == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(r.right + if (r.better == 1) "  ✓" else "", Modifier.weight(1f),
                        color = if (r.better == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun PhonesScreen(catalog: Catalog?) {
    val ctx = LocalContext.current
    val phones = catalog?.phones.orEmpty()
    var a by rememberSaveable { mutableIntStateOf(0) }
    var b by rememberSaveable { mutableIntStateOf(1) }
    ScreenFrame("Compare phones", "Pick two phones to see them side by side.") {
        Loading(catalog, phones.isEmpty(), "phones")
        if (phones.size >= 2) {
            val left = phones[a.coerceIn(phones.indices)]
            val right = phones[b.coerceIn(phones.indices)]
            Picker("Phone A", phones, a, { it.name }) { a = it }
            Spacer(Modifier.height(8.dp))
            Picker("Phone B", phones, b, { it.name }) { b = it }
            CompareTable(left.name, right.name, comparePhones(left, right))
            Row { listOf(left, right).forEach { p ->
                if (p.url.startsWith("https://")) TextButton(onClick = { open(ctx, p.url) }) { Text("${p.name} page") } } }
            Text("Found by the robot on the makers' pages and spec sites; \"not found yet\" means it has not located that figure. " +
                "Prices are starting prices and may differ by storage, carrier and sales.", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
fun CarsScreen(catalog: Catalog?) {
    val ctx = LocalContext.current
    val cars = catalog?.cars.orEmpty()
    var a by rememberSaveable { mutableIntStateOf(0) }
    var b by rememberSaveable { mutableIntStateOf(1) }
    ScreenFrame("Compare cars", "Pick two cars to see them side by side.") {
        Loading(catalog, cars.isEmpty(), "cars")
        if (cars.size >= 2) {
            val left = cars[a.coerceIn(cars.indices)]
            val right = cars[b.coerceIn(cars.indices)]
            Picker("Car A", cars, a, { it.name }) { a = it }
            Spacer(Modifier.height(8.dp))
            Picker("Car B", cars, b, { it.name }) { b = it }
            CompareTable(left.name, right.name, compareCars(left, right))
            Row { listOf(left, right).forEach { c ->
                if (c.url.startsWith("https://")) TextButton(onClick = { open(ctx, c.url) }) { Text("${c.name} page") } } }
            Text("Fuel economy is the US government's official EPA figure for one trim of each model (electric cars show MPGe, " +
                "which is not the same as gas MPG). Prices are found by the robot when the maker's page shows one.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}
