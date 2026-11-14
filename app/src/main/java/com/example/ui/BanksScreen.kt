package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.GatewaySettings
import com.example.data.RegisteredBankCard
import com.example.data.SmsParser

private val MirzaDarkBg = Color(0xFF141821)
private val MirzaCardBg = Color(0xFF1E2430)
private val MirzaInputBg = Color(0xFF181D27)
private val MirzaBorder = Color(0xFF2D3545)
private val MirzaGreen = Color(0xFF38D373)
private val MirzaMutedText = Color(0xFF8E98A8)
private val MirzaAddPillBg = Color(0xFF182C49)
private val MirzaAddPillText = Color(0xFF5392F7)

@Composable
fun BanksScreen(
    settings: GatewaySettings,
    onToggleFilterEnabled: (Boolean) -> Unit,
    onToggleBank: (String) -> Unit,
    onSelectAllBanks: (Boolean) -> Unit,
    onAddRegisteredBank: (bankCode: String, bankTitle: String, senderNumber: String) -> Unit = { _, _, _ -> },
    onRemoveRegisteredBank: (cardId: String) -> Unit = {},
    onLoadReceivedSenders: () -> List<String> = { emptyList() },
    modifier: Modifier = Modifier
) {
    var isAddingNewBank by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isAddingNewBank) {
        isAddingNewBank = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MirzaDarkBg)
    ) {
        Crossfade(
            targetState = isAddingNewBank,
            animationSpec = tween(durationMillis = 220),
            label = "banksSubScreenTransition"
        ) { addingMode ->
            if (addingMode) {
                AddNewBankScreen(
                    registeredCards = settings.registeredBankCards,
                    onLoadReceivedSenders = onLoadReceivedSenders,
                    onSubmitBank = { spec, sender ->
                        onAddRegisteredBank(spec.code, spec.cardTitleFa, sender)
                        isAddingNewBank = false
                    },
                    onCancel = { isAddingNewBank = false }
                )
            } else {
                RegisteredBanksMainScreen(
                    settings = settings,
                    onOpenAddBank = { isAddingNewBank = true },
                    onDeleteBankCard = onRemoveRegisteredBank,
                    onToggleFilterEnabled = onToggleFilterEnabled
                )
            }
        }
    }
}

@Composable
private fun RegisteredBanksMainScreen(
    settings: GatewaySettings,
    onOpenAddBank: () -> Unit,
    onDeleteBankCard: (String) -> Unit,
    onToggleFilterEnabled: (Boolean) -> Unit
) {
    var showParserLab by rememberSaveable { mutableStateOf(false) }
    var senderInput by rememberSaveable { mutableStateOf("98200033") }
    var parserTestInput by rememberSaveable {
        mutableStateOf("بانک ملت\nواریز به حساب شما\nمبلغ: 50,000,000ریال\nمانده: 90,000,000ریال")
    }
    var validationBanner by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Top Header: Title "بانک‌های تعریف شده" on the right (start in RTL) and "+ افزودن بانک" pill button on the left (end in RTL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "بانک‌های تعریف شده",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp
                ),
                color = Color.White
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MirzaAddPillBg,
                border = BorderStroke(1.dp, MirzaAddPillText.copy(alpha = 0.25f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenAddBank)
                    .testTag("add_bank_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن بانک",
                        tint = MirzaAddPillText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "افزودن بانک",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MirzaAddPillText
                    )
                }
            }
        }

        if (settings.registeredBankCards.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MirzaCardBg,
                border = BorderStroke(1.dp, MirzaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MirzaMutedText,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "هنوز بانکی تعریف نشده است",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "با زدن دکمه «+ افزودن بانک» در بالای صفحه، بانک و سرشماره پیامک آن را اضافه کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MirzaMutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (card in settings.registeredBankCards) {
                    val spec = BankVisualCatalog.findByCode(card.bankCode)
                    RealisticBankCard(
                        spec = spec,
                        bankTitleOverride = card.bankTitle,
                        senderNumber = card.senderNumber,
                        onDeleteClick = { onDeleteBankCard(card.id) },
                        modifier = Modifier.testTag("registered_bank_card_${card.id}")
                    )
                }
            }
        }

        // Collapsible Parser Lab & Automatic 50-Bank Detection Toggle
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MirzaCardBg,
            border = BorderStroke(1.dp, MirzaBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showParserLab = !showParserLab },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = MirzaGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "آزمایشگاه پارسر و تشخیص خودکار (${SmsParser.KNOWN_BANKS.size} بانک)",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تست زندهٔ استخراج مبلغ واریز و تشخیص سرشماره",
                                style = MaterialTheme.typography.labelSmall,
                                color = MirzaMutedText
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showParserLab) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MirzaMutedText
                    )
                }

                AnimatedVisibility(visible = showParserLab) {
                    val parsedPreview = SmsParser.parseFull(parserTestInput, senderInput)
                    val isPersonal = SmsParser.isPersonalSender(senderInput)
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "فیلتر محدودکننده بانک‌ها",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Switch(
                                checked = settings.filterBanksEnabled,
                                onCheckedChange = onToggleFilterEnabled,
                                modifier = Modifier.testTag("filter_banks_switch")
                            )
                        }
                        OutlinedTextField(
                            value = senderInput,
                            onValueChange = { senderInput = it },
                            label = { Text("فرستنده") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parser_tester_sender_input")
                        )
                        OutlinedTextField(
                            value = parserTestInput,
                            onValueChange = { parserTestInput = it },
                            label = { Text("متن نمونه پیامک بانکی") },
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parser_tester_input")
                        )
                        Text(
                            text = when {
                                isPersonal -> "ردشده: شماره موبایل شخصی"
                                parsedPreview.amountMinor != null ->
                                    "${parsedPreview.bankTitleFa} • واریز معتبر: ${SmsParser.formatToman(parsedPreview.amountMinor)} تومان"
                                else -> "ردشده: ${SmsParser.reasonLabelFa(parsedPreview.reason)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isPersonal && parsedPreview.amountMinor != null) MirzaGreen else Color(0xFFF87171),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (validationBanner != null) {
            Text(
                text = validationBanner.orEmpty(),
                color = Color(0xFFF87171),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AddNewBankScreen(
    registeredCards: List<RegisteredBankCard>,
    onLoadReceivedSenders: () -> List<String>,
    onSubmitBank: (spec: BankVisualSpec, senderNumber: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedBankCode by rememberSaveable { mutableStateOf<String?>(null) }
    var senderNumber by rememberSaveable { mutableStateOf("") }
    var bankFilterQuery by rememberSaveable { mutableStateOf("") }
    var showReceivedSendersModal by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedSpec = remember(selectedBankCode) {
        BankVisualCatalog.findByCode(selectedBankCode)
    }

    val filteredBanks = remember(bankFilterQuery) {
        val q = bankFilterQuery.trim()
        if (q.isEmpty()) {
            BankVisualCatalog.MIRZA_BANKS
        } else {
            BankVisualCatalog.MIRZA_BANKS.filter {
                it.gridLabelFa.contains(q, ignoreCase = true) ||
                    it.cardTitleFa.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true) ||
                    it.defaultSender.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header: "افزودن بانک جدید" + subtitle
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "افزودن بانک جدید",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp
                ),
                color = Color.White
            )
            Text(
                text = "بانک و سرشماره پیامک آن را انتخاب کنید",
                style = MaterialTheme.typography.bodyMedium,
                color = MirzaMutedText
            )
        }

        // 2. Live Interactive Bank Card Preview
        RealisticBankCard(
            spec = selectedSpec,
            senderNumber = senderNumber,
            onDeleteClick = null,
            modifier = Modifier.testTag("live_bank_card_preview")
        )

        // 3. Bank Selection Grid ("انتخاب بانک") - 4-column scrollable grid of all 33 Iranian banks
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "انتخاب بانک",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MirzaMutedText
                )

                // Compact inline search filter for the 33-bank grid
                Box(
                    modifier = Modifier
                        .width(185.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MirzaInputBg)
                        .border(1.dp, MirzaBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (bankFilterQuery.isEmpty()) {
                        Text(
                            text = "جستجوی سریع بانک...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MirzaMutedText.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = bankFilterQuery,
                        onValueChange = { bankFilterQuery = it },
                        singleLine = true,
                        cursorBrush = SolidColor(MirzaGreen),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_bank_search_input")
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MirzaCardBg,
                border = BorderStroke(1.dp, MirzaBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("mirza_bank_grid")
                ) {
                    items(
                        items = filteredBanks,
                        key = { it.code }
                    ) { bankSpec ->
                        val isSelected = selectedBankCode == bankSpec.code
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) MirzaGreen.copy(alpha = 0.12f) else Color.Transparent
                                )
                                .clickable {
                                    selectedBankCode = bankSpec.code
                                    validationError = null
                                }
                                .padding(vertical = 6.dp, horizontal = 2.dp)
                                .testTag("select_bank_item_${bankSpec.code}")
                        ) {
                            BankLogoBadge(
                                spec = bankSpec,
                                size = 56.dp,
                                isSelected = isSelected
                            )
                            Text(
                                text = bankSpec.gridLabelFa,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) MirzaGreen else Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 4. SMS Sender Input ("سرشماره پیامک") + Received Senders Modal Button
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "سرشماره پیامک",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MirzaMutedText
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Input Box on the right (in RTL, first item in Row is on the right)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MirzaInputBg)
                        .border(
                            width = 1.dp,
                            color = if (senderNumber.isNotEmpty()) MirzaGreen.copy(alpha = 0.55f) else MirzaBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (senderNumber.isEmpty()) {
                        Text(
                            text = "مثلاً: 98300017",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MirzaMutedText.copy(alpha = 0.75f)
                            )
                        )
                    }
                    BasicTextField(
                        value = senderNumber,
                        onValueChange = {
                            senderNumber = it
                            validationError = null
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(MirzaGreen),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDirection = TextDirection.Ltr,
                            textAlign = TextAlign.Right
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_sender_input")
                    )
                }

                // Square teal/blue list button on the left (second item in RTL Row)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF17324D),
                    border = BorderStroke(1.dp, Color(0xFF23496D)),
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showReceivedSendersModal = true }
                        .testTag("open_received_senders_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "سرشماره‌های دریافت‌شده",
                            tint = MirzaGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Text(
                text = "می‌توانید از دکمه لیست، سرشماره‌های دریافت‌شده را انتخاب کنید",
                style = MaterialTheme.typography.bodySmall,
                color = MirzaMutedText
            )

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF87171),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 5. Bottom Action Buttons: "انصراف" on the right and large green "+ ثبت بانک" on the left (matching Screenshot 2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(0.75f)
                    .height(52.dp)
                    .testTag("cancel_add_bank_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "انصراف",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MirzaMutedText
                    )
                )
            }

            Button(
                onClick = {
                    val spec = selectedSpec
                    val cleanSender = senderNumber.trim()
                    when {
                        spec == null -> {
                            validationError = "لطفاً ابتدا یکی از بانک‌ها را از لیست بالا انتخاب کنید."
                        }
                        cleanSender.isEmpty() -> {
                            validationError = "لطفاً سرشماره پیامک بانک را وارد یا از لیست انتخاب کنید."
                        }
                        else -> {
                            onSubmitBank(spec, cleanSender)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1.25f)
                    .height(52.dp)
                    .testTag("submit_add_bank_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MirzaGreen,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "+  ثبت بانک",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    ),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showReceivedSendersModal) {
        val sendersList = remember { onLoadReceivedSenders() }
        val registeredSendersSet = remember(registeredCards) {
            registeredCards.map { it.senderNumber.lowercase() }.toSet()
        }
        ReceivedSendersDialog(
            senders = sendersList,
            registeredSendersLower = registeredSendersSet,
            onSelectSender = { chosen ->
                senderNumber = chosen
                validationError = null
                if (selectedBankCode == null) {
                    val detected = SmsParser.detectBank(chosen, "")
                    if (BankVisualCatalog.findByCode(detected.code) != null) {
                        selectedBankCode = detected.code
                    }
                }
                showReceivedSendersModal = false
            },
            onDismiss = { showReceivedSendersModal = false }
        )
    }
}

/**
 * Dark modal matching Screenshot 1 ("سرشماره‌های دریافت‌شده") with "(قبلاً ثبت شده)" badge
 * for already-registered senders and a blue "بستن" button at the bottom left.
 */
@Composable
private fun ReceivedSendersDialog(
    senders: List<String>,
    registeredSendersLower: Set<String>,
    onSelectSender: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF3E4249),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 640.dp)
                .testTag("received_senders_modal")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "سرشماره‌های دریافت‌شده",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(senders) { sender ->
                        val isAlreadyRegistered = registeredSendersLower.contains(sender.lowercase())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isAlreadyRegistered) {
                                    onSelectSender(sender)
                                }
                                .padding(horizontal = 24.dp, vertical = 15.dp)
                                .testTag("received_sender_option_$sender"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isAlreadyRegistered) {
                                Text(
                                    text = "(قبلاً ثبت شده)",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp
                                    ),
                                    color = Color(0xFFFBBF24)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = sender,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 17.sp,
                                    fontWeight = if (isAlreadyRegistered) FontWeight.Normal else FontWeight.Medium,
                                    textDirection = TextDirection.Ltr
                                ),
                                color = if (isAlreadyRegistered) Color.White.copy(alpha = 0.55f) else Color.White
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_received_senders_modal_button")
                    ) {
                        Text(
                            text = "بستن",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
            }
        }
    }
}
