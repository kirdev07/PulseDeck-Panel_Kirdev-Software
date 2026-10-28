package com.kirdev.pulsedeckpanel.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.ui.ScreenHeader
import com.kirdev.pulsedeckpanel.core.ui.VSpace
import com.kirdev.pulsedeckpanel.data.model.DiscoveredPc
import com.kirdev.pulsedeckpanel.feature.connection.ConnectionViewModel.Step

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel, onBack: (() -> Unit)?, onPaired: () -> Unit) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    LaunchedEffect(step) { if (step is Step.Paired) onPaired() }
    val context = LocalContext.current
    val scan = { QrScanner.scan(context, viewModel::pairFromQr) { viewModel.showError(it) } }

    Column(Modifier.fillMaxSize().background(Palette.Black)) {
        val title = if (step is Step.Found) "Выбор устройства" else "Подключение к ПК"
        if (onBack != null) ScreenHeader(title, onBack) else {
            Text(title, fontSize = 18.sp, color = Palette.Text, modifier = Modifier.padding(start = 24.dp, top = 18.dp))
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            when (val current = step) {
                Step.Searching -> Searching(onCancel = viewModel::cancel)
                is Step.Found -> Found(current.pcs, viewModel::choose, viewModel::search, viewModel::manual, scan)
                Step.Manual -> Manual(viewModel::connectManual, viewModel::search)
                is Step.Probing -> Message("Связываюсь с ${current.host}…", progress = true)
                is Step.EnterCode -> EnterCode(current, viewModel::submit, viewModel::search)
                is Step.Failed -> Failed(current.message, viewModel::search, viewModel::manual, scan)
                Step.Paired -> Message("Подключено", progress = false)
            }
        }
    }
}

@Composable
private fun Searching(onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(PanelIcons.Monitor, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(36.dp))
        VSpace(14.dp)
        Text("Поиск PulseDeck в сети…", fontSize = 16.sp, color = Palette.Text)
        VSpace(4.dp)
        Text("Убедитесь, что PulseDeck запущен на ПК и телефон в той же сети Wi-Fi.", fontSize = 12.sp, color = Palette.Text2)
        VSpace(18.dp)
        CircularProgressIndicator(color = Palette.Text2, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        VSpace(18.dp)
        OutlinedButton(onClick = onCancel) { Text("Отменить", color = Palette.Text2) }
    }
}

@Composable
private fun Found(pcs: List<DiscoveredPc>, onChoose: (DiscoveredPc) -> Unit, onSearch: () -> Unit, onManual: () -> Unit,
                  onScan: () -> Unit) {
    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pcs, key = { it.host }) { pc -> PcRow(pc, onChoose) }
        }
        if (pcs.isEmpty()) Text("Список пуст.", fontSize = 13.sp, color = Palette.Text3)
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (QR_SCAN_ENABLED) OutlinedButton(onClick = onScan) { Text("Сканировать QR-код", color = Palette.Text) }
            TextButton(onClick = onSearch) { Text("Искать снова", color = Palette.Text2) }
            TextButton(onClick = onManual) { Text("Ввести адрес вручную", color = Palette.Text2) }
        }
        VSpace(10.dp)
        UsbHint()
    }
}

/** First pairing over the cable: what to switch on. */
@Composable
private fun UsbHint() {
    Text(
        "Нет Wi-Fi? Подключите телефон кабелем к ПК: включите «Параметры разработчика» (7 нажатий на «Номер сборки») " +
            "и «Отладка по USB», разрешите отладку для этого ПК. PulseDeck сам направит связь через кабель. " +
            "Другой вариант — «USB-модем» в настройках точки доступа.",
        fontSize = 11.sp, color = Palette.Text3, lineHeight = 15.sp,
    )
}

@Composable
private fun PcRow(pc: DiscoveredPc, onChoose: (DiscoveredPc) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = pc.supported) { onChoose(pc) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PanelIcons.Monitor, contentDescription = null, tint = Palette.Text2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pc.name, fontSize = 15.sp, color = if (pc.supported) Palette.Text else Palette.Text3)
            Text(if (pc.supported) pc.host else "${pc.host} · обновите PulseDeck до 1.0", fontSize = 12.sp, color = Palette.Text3)
        }
        if (pc.supported) Icon(PanelIcons.Chevron, contentDescription = null, tint = Palette.Text3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Manual(onConnect: (String) -> Unit, onSearch: () -> Unit) {
    var address by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Адрес ПК — на экране «Телефон» в PulseDeck.", fontSize = 13.sp, color = Palette.Text2)
        VSpace(12.dp)
        OutlinedTextField(
            value = address, onValueChange = { address = it.take(32) }, singleLine = true,
            placeholder = { Text("192.168.1.5", color = Palette.Text3) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onConnect(address) }),
            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onConnect(address) }, enabled = address.isNotBlank()) { Text("Подключить", color = Palette.Text) }
            TextButton(onClick = onSearch) { Text("Поиск в сети", color = Palette.Text2) }
        }
    }
}

@Composable
private fun EnterCode(step: Step.EnterCode, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var code by remember(step.host) { mutableStateOf("") }
    Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(step.name, fontSize = 16.sp, color = Palette.Text)
        VSpace(6.dp)
        Text("На ПК откройте «Телефон» → «Подключить по коду». Сверьте отпечаток и введите код.",
            fontSize = 12.sp, color = Palette.Text2, textAlign = TextAlign.Center)
        VSpace(12.dp)
        Text("Отпечаток ПК", fontSize = 11.sp, color = Palette.Text3)
        Text(ConnectionViewModel.shortFingerprint(step.fingerprint), fontSize = 18.sp, color = Palette.Text,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text("Если он не совпадает с экраном ПК — не продолжайте.", fontSize = 11.sp, color = Palette.Text3)
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                placeholder = { Text("000000", color = Palette.Text3) }, enabled = !step.sending,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(code) }),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp,
                    letterSpacing = 4.sp, color = Palette.Text),
                colors = fieldColors(), modifier = Modifier.width(190.dp),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { onSubmit(code) }, enabled = code.length == 6 && !step.sending) {
                Text(if (step.sending) "Подключаю…" else "Подключить", color = Palette.Text)
            }
        }
        step.error?.let { VSpace(8.dp); Text(it, fontSize = 12.sp, color = Palette.Text, textAlign = TextAlign.Center) }
        TextButton(onClick = onBack) { Text("Выбрать другой ПК", color = Palette.Text3) }
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onManual: () -> Unit, onScan: () -> Unit) {
    Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, fontSize = 14.sp, color = Palette.Text, textAlign = TextAlign.Center)
        VSpace(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Искать снова", color = Palette.Text) }
            if (QR_SCAN_ENABLED) OutlinedButton(onClick = onScan) { Text("Сканировать QR-код", color = Palette.Text) }
            TextButton(onClick = onManual) { Text("Ввести адрес", color = Palette.Text2) }
        }
        VSpace(14.dp)
        UsbHint()
    }
}

@Composable
private fun Message(text: String, progress: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) {
            CircularProgressIndicator(color = Palette.Text2, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            VSpace(12.dp)
        }
        Text(text, fontSize = 14.sp, color = Palette.Text2)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Text2, unfocusedBorderColor = Palette.Border, cursorColor = Palette.Text,
    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
)

/** QR pairing is off for now: weak phone cameras could not read the code from a monitor. */
private const val QR_SCAN_ENABLED = true
