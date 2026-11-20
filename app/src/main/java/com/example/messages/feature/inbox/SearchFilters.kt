package com.example.messages.feature.inbox

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.messages.data.sms.MessageCategory
import com.example.messages.data.sms.MessageSearch
import com.example.messages.data.sms.SearchField
import java.time.LocalDate

@Composable
internal fun SearchFilters(initial: MessageSearch, onClose: () -> Unit, onApply: (MessageSearch) -> Unit) {
    val context = LocalContext.current
    var criteria by remember { mutableStateOf(initial) }
    fun pick(current: LocalDate?, update: (LocalDate) -> Unit) {
        val date = current ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, d -> update(LocalDate.of(y, m + 1, d)) },
            date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    val valid = criteria.from?.let { from -> criteria.through?.let { it >= from } } ?: true
    AlertDialog(onDismissRequest = onClose, title = { Text("Search filters") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchField.entries.forEach { field ->
                        FilterChip(selected = criteria.field == field, onClick = { criteria = criteria.copy(field = field) },
                            label = { Text(field.name) })
                    }
                }
                TextButton(onClick = { pick(criteria.from) { criteria = criteria.copy(from = it) } }) {
                    Text("From ${criteria.from ?: "any date"}")
                }
                TextButton(onClick = { pick(criteria.through) { criteria = criteria.copy(through = it) } }) {
                    Text("Through ${criteria.through ?: "any date"}")
                }
                Text("Category")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(null) + MessageCategory.entries).forEach { category ->
                        FilterChip(selected = !criteria.spamOnly && criteria.category == category, onClick = { criteria = criteria.copy(category = category, spamOnly = false) },
                            label = { Text(category?.name ?: "Any") })
                    }
                    FilterChip(selected = criteria.spamOnly, onClick = { criteria = criteria.copy(category = null, spamOnly = true) },
                        label = { Text("Spam") })
                }
                if (!valid) Text("The end date must be on or after the start date.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onApply(criteria) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { onApply(MessageSearch()) }) { Text("Reset filters") } })
}
