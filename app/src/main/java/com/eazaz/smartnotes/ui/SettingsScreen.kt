package com.eazaz.smartnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazaz.smartnotes.NotesViewModel
import com.eazaz.smartnotes.R
import com.eazaz.smartnotes.calc.CalcEngine
import com.eazaz.smartnotes.data.CalcSettings

private val SYMBOLS = listOf('+', '-', '*', '/')

private fun symbolText(op: Char): String = when (op) {
    '-' -> "−"
    '*' -> "×"
    '/' -> "÷"
    else -> "+"
}

private fun nextSymbol(op: Char): Char = SYMBOLS[(SYMBOLS.indexOf(op) + 1) % SYMBOLS.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: CalcSettings, vm: NotesViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var newWord by remember { mutableStateOf("") }
    var newOp by remember { mutableStateOf('+') }

    fun addWord() {
        if (newWord.isNotBlank()) {
            vm.setWord(newWord, newOp)
            newWord = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calculation)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ---------- default mode
            SectionTitle(stringResource(R.string.default_mode))
            listOf(
                CalcEngine.Mode.AUTO to R.string.mode_auto_desc,
                CalcEngine.Mode.MANUAL to R.string.mode_manual_desc,
                CalcEngine.Mode.OFF to R.string.mode_off_desc,
            ).forEach { (mode, desc) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setDefaultMode(mode) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(selected = settings.defaultMode == mode, onClick = null)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(modeName(mode), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---------- default symbol
            SectionTitle(stringResource(R.string.default_symbol))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SYMBOLS.forEach { op ->
                    FilterChip(
                        selected = settings.defaultOp == op,
                        onClick = { vm.setDefaultOp(op) },
                        label = { Text(symbolText(op), fontSize = 18.sp, fontWeight = FontWeight.Medium) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ---------- words
            SectionTitle(stringResource(R.string.calc_words))
            Text(stringResource(R.string.calc_words_help), style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(8.dp))

            settings.words.entries.sortedWith(compareBy<Map.Entry<String, Char>>({ it.value }, { it.key })).forEach { (word, op) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    SymbolButton(op) { vm.setWord(word, nextSymbol(op)) }
                    Spacer(Modifier.width(14.dp))
                    Text(word, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeWord(word) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = muted)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolButton(newOp) { newOp = nextSymbol(newOp) }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it.replace(" ", "") },
                    placeholder = { Text(stringResource(R.string.add_word)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addWord() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { addWord() }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
            }
            TextButton(onClick = { vm.resetWords() }) { Text(stringResource(R.string.reset_defaults)) }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.heading_tip), style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.calc_tip), style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SymbolButton(op: Char, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            symbolText(op),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}
