package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.ui.components.AsisMotion

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
        AnimatedContent(
            targetState = isAddingNewBank,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220)) +
                    slideInVertically(
                        animationSpec = AsisMotion.offsetSpring()
                    ) { fullHeight -> if (targetState) fullHeight / 12 else -fullHeight / 12 } +
                    scaleIn(initialScale = 0.96f, animationSpec = AsisMotion.bouncySpring()))
                    .togetherWith(
                        fadeOut(animationSpec = tween(160)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(160))
                    )
            },
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
    var removingCardIds by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Top Header: Title "بانک‌های تعریف شده" + active counter pill on the right, and "+ افزودن بانک" pill button on the left
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "بانک‌های تعریف شده",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp
                    ),
                    color = Color.White
                )
                AnimatedVisibility(
                    visible = settings.registeredBankCards.isNotEmpty(),
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = AsisMotion.bouncySpring()),
                    exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.85f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MirzaGreen.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MirzaGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${settings.registeredBankCards.size} فعال",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MirzaGreen,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }

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

        AnimatedVisibility(
            visible = settings.registeredBankCards.isEmpty(),
            enter = fadeIn(tween(240)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.94f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(180)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.94f)
        ) {
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
        }

        AnimatedVisibility(
            visible = settings.registeredBankCards.isNotEmpty(),
            enter = fadeIn(tween(240)) + expandVertically(),
            exit = fadeOut(tween(180)) + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = AsisMotion.smoothSpring()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (card in settings.registeredBankCards) {
                    key(card.id) {
                        var isCardEntered by remember { mutableStateOf(false) }
                        val isCardRemoving = removingCardIds.contains(card.id)

                        LaunchedEffect(card.id) {
                            isCardEntered = true
                        }

                        LaunchedEffect(isCardRemoving) {
                            if (isCardRemoving) {
                                kotlinx.coroutines.delay(190)
                                onDeleteBankCard(card.id)
                                removingCardIds = removingCardIds - card.id
                            }
                        }

                        AnimatedVisibility(
                            visible = isCardEntered && !isCardRemoving,
                            enter = fadeIn(tween(230)) +
                                expandVertically(animationSpec = AsisMotion.bouncySpring()) +
                                slideInVertically(animationSpec = AsisMotion.offsetSpring()) { it / 6 } +
                                scaleIn(initialScale = 0.93f, animationSpec = AsisMotion.bouncySpring()),
                            exit = fadeOut(tween(170)) +
                                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                                scaleOut(targetScale = 0.92f, animationSpec = tween(170))
                        ) {
                            val spec = BankVisualCatalog.findByCode(card.bankCode)
                            RealisticBankCard(
                                spec = spec,
                                bankTitleOverride = card.bankTitle,
                                senderNumber = card.senderNumber,
                                onDeleteClick = {
                                    removingCardIds = removingCardIds + card.id
                                },
                                modifier = Modifier.testTag("registered_bank_card_${card.id}")
                            )
                        }
                    }
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

                AnimatedVisibility(
                    visible = showParserLab,
                    enter = fadeIn(tween(220)) + expandVertically(animationSpec = AsisMotion.smoothSpring()),
                    exit = fadeOut(tween(160)) + shrinkVertically(animationSpec = AsisMotion.smoothSpring())
                ) {
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
                        val itemScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            animationSpec = AsisMotion.snappySpring(),
                            label = "bankGridScale_${bankSpec.code}"
                        )
                        val itemBg by animateColorAsState(
                            targetValue = if (isSelected) MirzaGreen.copy(alpha = 0.14f) else Color.Transparent,
                            animationSpec = AsisMotion.smoothSpring(),
                            label = "bankGridBg_${bankSpec.code}"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(200),
                                    placementSpec = AsisMotion.offsetSpring(),
                                    fadeOutSpec = tween(150)
                                )
                                .scale(itemScale)
                                .clip(RoundedCornerShape(14.dp))
                                .background(itemBg)
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

            AnimatedVisibility(
                visible = selectedSpec != null && senderNumber.trim() != selectedSpec.defaultSender,
                enter = fadeIn(tween(220)) +
                    expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                    scaleIn(initialScale = 0.94f, animationSpec = AsisMotion.bouncySpring()),
                exit = fadeOut(tween(150)) +
                    shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                    scaleOut(targetScale = 0.94f)
            ) {
                val specForChip = selectedSpec
                if (specForChip != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MirzaGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MirzaGreen.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                senderNumber = specForChip.defaultSender
                                validationError = null
                            }
                            .testTag("use_default_sender_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MirzaGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "درج سرشماره پیش‌فرض ${specForChip.gridLabelFa}: ${specForChip.defaultSender}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MirzaGreen
                            )
                        }
                    }
                }
            }

            Text(
                text = "می‌توانید از دکمه لیست، بین سرشماره‌های دریافت‌شده جستجو و انتخاب کنید",
                style = MaterialTheme.typography.bodySmall,
                color = MirzaMutedText
            )

            AnimatedVisibility(
                visible = validationError != null,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
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
        val sendersList = remember(showReceivedSendersModal) { onLoadReceivedSenders() }
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

private enum class SenderFilterMode(val titleFa: String) {
    ALL("همه"),
    BANKS_ONLY("سرشماره بانکی"),
    NON_PERSONAL("خدماتی / غیرشخصی")
}

private data class EnrichedSenderItem(
    val sender: String,
    val bankSpec: BankVisualSpec?,
    val detectedBankTitleFa: String?,
    val isPersonal: Boolean,
    val isAlreadyRegistered: Boolean
)

private fun normalizeSearchText(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
        when (ch) {
            in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
            in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
            'ي' -> sb.append('ی')
            'ك' -> sb.append('ک')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/**
 * Pro Max Dark Modal ("سرشماره‌های دریافت‌شده") with live search bar, category filters,
 * automatic Iranian bank recognition badges, and "(قبلاً ثبت شده)" status indicators.
 */
@Composable
private fun ReceivedSendersDialog(
    senders: List<String>,
    registeredSendersLower: Set<String>,
    onSelectSender: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf(SenderFilterMode.ALL) }

    val enrichedSenders = remember(senders, registeredSendersLower) {
        senders.map { sender ->
            val detected = SmsParser.detectBank(sender, "")
            val visualSpec = BankVisualCatalog.findByCode(detected.code)
            val bankTitle = visualSpec?.cardTitleFa
                ?: detected.titleFa.takeIf { detected.code != "unknown" && detected.code != "other" }
            EnrichedSenderItem(
                sender = sender,
                bankSpec = visualSpec,
                detectedBankTitleFa = bankTitle,
                isPersonal = SmsParser.isPersonalSender(sender),
                isAlreadyRegistered = registeredSendersLower.contains(sender.lowercase())
            )
        }
    }

    val bankSendersCount = remember(enrichedSenders) {
        enrichedSenders.count { it.bankSpec != null || it.detectedBankTitleFa != null }
    }
    val nonPersonalCount = remember(enrichedSenders) {
        enrichedSenders.count { !it.isPersonal }
    }

    val filteredSenders = remember(enrichedSenders, searchQuery, filterMode) {
        val rawQ = searchQuery.trim()
        val normalizedQ = normalizeSearchText(rawQ).lowercase()
        enrichedSenders.filter { item ->
            val modeMatches = when (filterMode) {
                SenderFilterMode.ALL -> true
                SenderFilterMode.BANKS_ONLY -> item.bankSpec != null || item.detectedBankTitleFa != null
                SenderFilterMode.NON_PERSONAL -> !item.isPersonal
            }
            if (!modeMatches) return@filter false
            if (normalizedQ.isEmpty()) return@filter true

            val senderNorm = normalizeSearchText(item.sender).lowercase()
            val bankTitleNorm = normalizeSearchText(item.detectedBankTitleFa.orEmpty()).lowercase()
            val gridLabelNorm = normalizeSearchText(item.bankSpec?.gridLabelFa.orEmpty()).lowercase()
            val bankCodeNorm = item.bankSpec?.code?.lowercase().orEmpty()

            senderNorm.contains(normalizedQ) ||
                bankTitleNorm.contains(normalizedQ) ||
                gridLabelNorm.contains(normalizedQ) ||
                bankCodeNorm.contains(normalizedQ)
        }
    }

    var modalVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var pendingSelectedSender by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        modalVisible = true
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            modalVisible = false
            kotlinx.coroutines.delay(150)
            val chosen = pendingSelectedSender
            if (chosen != null) {
                onSelectSender(chosen)
            } else {
                onDismiss()
            }
        }
    }

    val triggerDismiss = {
        pendingSelectedSender = null
        isClosing = true
    }
    val triggerSelect: (String) -> Unit = { chosen ->
        pendingSelectedSender = chosen
        isClosing = true
    }

    Dialog(
        onDismissRequest = triggerDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = modalVisible,
            enter = fadeIn(tween(210)) +
                scaleIn(
                    initialScale = 0.90f,
                    animationSpec = AsisMotion.bouncySpring()
                ) +
                slideInVertically(
                    animationSpec = AsisMotion.offsetSpring()
                ) { it / 10 },
            exit = fadeOut(tween(160)) +
                scaleOut(targetScale = 0.90f, animationSpec = tween(160)) +
                slideOutVertically(animationSpec = tween(160)) { it / 10 }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF161B26),
                border = BorderStroke(1.dp, Color(0xFF2B3548)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth(0.93f)
                    .heightIn(max = 680.dp)
                    .animateContentSize(animationSpec = AsisMotion.smoothSpring())
                    .testTag("received_senders_modal")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Header with icon, title, and live counter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MirzaGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MirzaGreen.copy(alpha = 0.32f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = MirzaGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "سرشماره‌های دریافت‌شده",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "${filteredSenders.size} سرشماره از ${senders.size} مورد • اولویت با سرشماره‌های بانکی",
                                style = MaterialTheme.typography.labelSmall,
                                color = MirzaMutedText
                            )
                        }
                    }
                }

                // 2. Search Bar inside the modal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F141D))
                        .border(
                            width = 1.dp,
                            color = if (searchQuery.isNotEmpty()) MirzaGreen.copy(alpha = 0.65f) else Color(0xFF283246),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "جستجو",
                            tint = if (searchQuery.isNotEmpty()) MirzaGreen else MirzaMutedText,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "جستجوی شماره، سرشماره یا نام بانک (مثلاً ملت، 2000)...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MirzaMutedText.copy(alpha = 0.78f),
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                cursorBrush = SolidColor(MirzaGreen),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("received_senders_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF252F42),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" }
                                    .testTag("received_senders_clear_search_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "پاک کردن جستجو",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Category Filter Pills ("همه", "سرشماره بانکی", "خدماتی / غیرشخصی")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterOptions = listOf(
                        Triple(SenderFilterMode.ALL, "همه (${senders.size})", "received_senders_filter_all"),
                        Triple(SenderFilterMode.BANKS_ONLY, "بانکی ($bankSendersCount)", "received_senders_filter_banks"),
                        Triple(SenderFilterMode.NON_PERSONAL, "خدماتی ($nonPersonalCount)", "received_senders_filter_service")
                    )
                    for ((mode, label, tag) in filterOptions) {
                        val selected = filterMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MirzaGreen.copy(alpha = 0.16f) else Color(0xFF1D2433),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MirzaGreen.copy(alpha = 0.55f) else Color(0xFF2B3548)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { filterMode = mode }
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (selected) MirzaGreen else MirzaMutedText,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF252E40)
                )

                // 4. Senders List or Empty Search State
                AnimatedContent(
                    targetState = filteredSenders.isEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = AsisMotion.bouncySpring()))
                            .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.97f))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    label = "receivedSendersEmptyOrList"
                ) { isEmpty ->
                    if (isEmpty) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MirzaMutedText,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = "سرشماره‌ای مطابق با «$searchQuery» یافت نشد",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "می‌توانید فیلتر را تغییر دهید یا عبارت جستجو شده را مستقیماً به عنوان سرشماره ثبت کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MirzaMutedText,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (searchQuery.trim().isNotEmpty()) {
                                    Button(
                                        onClick = { triggerSelect(normalizeSearchText(searchQuery.trim())) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MirzaGreen,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("use_searched_sender_directly_button")
                                    ) {
                                        Text(
                                            text = "انتخاب «${searchQuery.trim()}»",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        filterMode = SenderFilterMode.ALL
                                    }
                                ) {
                                    Text(
                                        text = "نمایش همه سرشماره‌ها",
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = filteredSenders,
                                key = { it.sender }
                            ) { item ->
                                val isAlreadyRegistered = item.isAlreadyRegistered
                                val isRecognizedBank = item.bankSpec != null || item.detectedBankTitleFa != null

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        isAlreadyRegistered -> Color(0xFF1A202C).copy(alpha = 0.65f)
                                        isRecognizedBank -> Color(0xFF1E293B)
                                        else -> Color(0xFF1A2130)
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = when {
                                            isAlreadyRegistered -> Color(0xFFFBBF24).copy(alpha = 0.28f)
                                            isRecognizedBank -> MirzaGreen.copy(alpha = 0.35f)
                                            else -> Color(0xFF283246)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            fadeInSpec = tween(200),
                                            placementSpec = AsisMotion.offsetSpring(),
                                            fadeOutSpec = tween(150)
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(enabled = !isAlreadyRegistered) {
                                            triggerSelect(item.sender)
                                        }
                                        .testTag("received_sender_option_${item.sender}")
                                ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Right side in RTL: Bank Logo or SMS Icon + Bank/Category label
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (item.bankSpec != null) {
                                            BankLogoBadge(
                                                spec = item.bankSpec,
                                                size = 40.dp,
                                                isSelected = false
                                            )
                                        } else {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (item.isPersonal) {
                                                    Color(0xFF2B3346)
                                                } else {
                                                    Color(0xFF1E3A5F)
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Sms,
                                                        contentDescription = null,
                                                        tint = if (item.isPersonal) MirzaMutedText else Color(0xFF60A5FA),
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = item.sender,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = FontFamily.SansSerif,
                                                    fontSize = 16.sp,
                                                    fontWeight = if (isAlreadyRegistered) FontWeight.Medium else FontWeight.ExtraBold,
                                                    textDirection = TextDirection.Ltr,
                                                    textAlign = TextAlign.Right
                                                ),
                                                color = if (isAlreadyRegistered) Color.White.copy(alpha = 0.55f) else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = when {
                                                    item.detectedBankTitleFa != null -> item.detectedBankTitleFa
                                                    item.isPersonal -> "شماره موبایل"
                                                    else -> "سرشماره خدماتی / پیامکی"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = if (item.detectedBankTitleFa != null) FontWeight.Bold else FontWeight.Normal
                                                ),
                                                color = when {
                                                    item.detectedBankTitleFa != null -> MirzaGreen
                                                    else -> MirzaMutedText
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Left side in RTL: Status / Action Badge
                                    if (isAlreadyRegistered) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFBBF24).copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "(قبلاً ثبت شده)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = Color(0xFFFBBF24),
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MirzaGreen.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, MirzaGreen.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = "انتخاب",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                ),
                                                color = MirzaGreen,
                                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF252E40)
                )

                // 5. Footer with Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "با لمس هر سرشماره، بانک مرتبط نیز خودکار انتخاب می‌شود",
                        style = MaterialTheme.typography.labelSmall,
                        color = MirzaMutedText,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = triggerDismiss,
                        modifier = Modifier.testTag("close_received_senders_modal_button")
                    ) {
                        Text(
                            text = "بستن",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
            }
        }
        }
    }
}
