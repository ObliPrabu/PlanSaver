package com.obliprabu.plansaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { PlanSaverApp(BundledPlanRepository()) } } }
    }
}

private val dataChoices = listOf("1-5 GB" to 5, "10 GB" to 10, "25 GB" to 25, "Unlimited" to null)

@Composable
fun PlanSaverApp(repo: PlanRepository) {
    var lines by remember { mutableIntStateOf(1) }
    var dataIdx by remember { mutableIntStateOf(3) }
    var current by remember { mutableStateOf("") }
    var postpaid by remember { mutableStateOf(true) }
    var freePhone by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Result>?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
    ) {
        Text("PlanSaver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp))
        Text("Find the cheapest US phone plan for what you actually use.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("How many lines?", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { n ->
                    FilterChip(selected = lines == n, onClick = { lines = n }, label = { Text(if (n == 4) "4+" else "$n") })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Data per line", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dataChoices.forEachIndexed { i, (label, _) ->
                    FilterChip(selected = dataIdx == i, onClick = { dataIdx = i }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = current,
                onValueChange = { v -> current = v.filter { it.isDigit() || it == '.' }.take(7) },
                label = { Text("What you pay now per month (total)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Checkbox(checked = postpaid, onCheckedChange = { postpaid = it })
                Text("Include big-carrier plans (Verizon, AT&T, T-Mobile)")
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Checkbox(checked = freePhone, onCheckedChange = { freePhone = it })
                Text("Only carriers offering a free or discounted phone")
            }

            // Every tap runs a fresh search.
            Button(
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    loading = true
                    scope.launch {
                        results = try {
                            repo.search(SearchRequest(lines, dataChoices[dataIdx].second, current.toDoubleOrNull() ?: 0.0, postpaid, freePhone))
                        } catch (e: Exception) {
                            emptyList()
                        }
                        loading = false
                    }
                },
            ) { Text(if (loading) "Searching..." else "Search plans") }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))

            results?.let { list ->
                Spacer(Modifier.height(16.dp))
                if (list.isEmpty()) Text("No plans matched. Try more data or include big carriers.")
                else list.forEach { ResultCard(it, current.toDoubleOrNull() != null) }
                Text("Prices researched Oct 2026 and may have changed - confirm on the carrier's site. " +
                    "PlanSaver may earn a commission if you sign up through a link.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
    }
}

@Composable
private fun ResultCard(r: Result, showSavings: Boolean) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${r.plan.carrier} - ${r.plan.name}", fontWeight = FontWeight.Bold)
            Text("${r.plan.dataGb?.let { "$it GB" } ?: "Unlimited"} - ${r.plan.network} network - ${if (r.plan.prepaid) "prepaid" else "postpaid"}",
                style = MaterialTheme.typography.bodySmall)
            if (r.plan.note.isNotEmpty()) Text(r.plan.note, style = MaterialTheme.typography.bodySmall)
            r.plan.deal?.let {
                Text((if (r.plan.freePhoneOffer) "Phone deal: " else "Deal: ") + it,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                if (r.plan.freePhoneOffer) Text("Usually needs a trade-in and 24-36 months of bill credits - leaving early forfeits them.",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (r.plan.perks.isNotEmpty()) Text("Perks: " + r.plan.perks.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "$%.2f / month", r.monthly), style = MaterialTheme.typography.titleMedium)
            if (showSavings && r.yearlySavings > 0)
                Text(String.format(Locale.US, "Save about $%.0f a year", r.yearlySavings), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.plan.signupUrl))) }) { Text("View plan") }
        }
    }
}
