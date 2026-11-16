package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomFilterRule
import com.example.data.FilterAction
import com.example.data.FilterTarget
import com.example.data.GatewaySettings
import com.example.data.SmsParser

private data class FilterPreset(
    val label: String,
    val pattern: String,
    val target: FilterTarget
)

private val QUICK_FILTER_PRESETS = listOf(
    FilterPreset("سود سپرده", "سود", FilterTarget.BODY_KEYWORD),
    FilterPreset("کارمزد / آبونمان", "کارمزد", FilterTarget.BODY_KEYWORD),
    FilterPreset("اقساط / تسهیلات", "قسط", FilterTarget.BODY_KEYWORD),
    FilterPreset("رمز پویا", "رمز پویا", FilterTarget.BODY_KEYWORD),
    FilterPreset("مبالغ زیر ۱٬۰۰۰ تومان", "1000", FilterTarget.MIN_AMOUNT_TOMAN)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomFilterRulesCard(
    settings: GatewaySettings,
    onToggleMasterEnabled: (Boolean) -> Unit,
    onAddRule: (name: String, pattern: String, target: FilterTarget, action: FilterAction) -> Unit,
    onToggleRule: (ruleId: String) -> Unit,
    onRemoveRule: (ruleId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val activeRulesCount = settings.customFilterRules.count { it.enabled }
    val hasRules = settings.customFilterRules.isNotEmpty()

    AsisElevatedCard(
        modifier = modifier
            .animateContentSize(animationSpec = AsisMotion.smoothSpring())
            .testTag("custom_filter_rules_card"),
        borderColor = if (settings.customFilterRulesEnabled && activeRulesCount > 0) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SectionHeader(
                    icon = Icons.Default.FilterAlt,
                    title = "قوانین فیلتر سفارشی پیامک",
                    subtitle = if (settings.customFilterRulesEnabled) {
                        "$activeRulesCount قانون فعال • کاهش بار سرور با حذف خودکار پیامک‌های غیرمرتبط"
                    } else {
                        "غیرفعال: تمام پیامک‌های بانکی به صف تطبیق و سرور ارسال می‌شوند"
                    }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = settings.customFilterRulesEnabled,
                onCheckedChange = onToggleMasterEnabled,
                modifier = Modifier.testTag("custom_filters_master_switch")
            )
        }

        AnimatedVisibility(
            visible = settings.customFilterRulesEnabled,
            enter = fadeIn(tween(220)) +
                expandVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleIn(initialScale = 0.97f, animationSpec = AsisMotion.bouncySpring()),
            exit = fadeOut(tween(150)) +
                shrinkVertically(animationSpec = AsisMotion.smoothSpring()) +
                scaleOut(targetScale = 0.97f, animationSpec = tween(150))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Server load saved summary bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "صرفه‌جویی در ترافیک سرور",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        StatusPill(
                            label = "${settings.blockedByCustomFiltersCount} پیامک مسدودشده",
                            color = MaterialTheme.colorScheme.secondary,
                            icon = Icons.Default.Block
                        )
                    }
                }

                // Quick-add one-tap suggestions
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "افزودن سریع قوانین پرکاربرد:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QUICK_FILTER_PRESETS.forEach { preset ->
                            val alreadyExists = settings.customFilterRules.any {
                                it.target == preset.target &&
                                    it.pattern.equals(preset.pattern, ignoreCase = true)
                            }
                            SuggestionChip(
                                onClick = {
                                    if (!alreadyExists) {
                                        onAddRule(
                                            preset.label,
                                            preset.pattern,
                                            preset.target,
                                            FilterAction.IGNORE_LOG_ONLY
                                        )
                                    }
                                },
                                enabled = !alreadyExists,
                                label = {
                                    Text(
                                        text = if (alreadyExists) "✓ ${preset.label}" else "+ ${preset.label}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.testTag("preset_filter_${preset.pattern}")
                            )
                        }
                    }
                }

                if (!hasRules) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هنوز قانونی تعریف نشده است. می‌توانید کلمات کلیدی (مانند «سود»، «قسط» یا «کارمزد»)، سرشماره یا حداقل مبلغ واریز را فیلتر کنید تا درخواست‌های غیرضروری به سرور ارسال نشوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (rule in settings.customFilterRules) {
                            CustomFilterRuleRow(
                                rule = rule,
                                onToggle = { onToggleRule(rule.id) },
                                onDelete = { onRemoveRule(rule.id) }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_custom_filter_rule_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن قانون فیلتر جدید",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تعریف قانون فیلتر سفارشی",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomFilterRuleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, pattern, target, action ->
                showAddDialog = false
                onAddRule(name, pattern, target, action)
            }
        )
    }
}

@Composable
private fun CustomFilterRuleRow(
    rule: CustomFilterRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (rule.enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (rule.enabled) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_filter_rule_item_${rule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rule.displayTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rule.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val patternPreview = if (rule.target == FilterTarget.MIN_AMOUNT_TOMAN) {
                    val toman = rule.pattern.toLongOrNull() ?: 0L
                    "< ${SmsParser.formatRial(toman)} تومان"
                } else {
                    "«${rule.pattern}»"
                }
                Text(
                    text = "${rule.target.labelFa}: $patternPreview • ${rule.action.shortFa}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (rule.hitCount > 0L) {
                    Text(
                        text = "${rule.hitCount} پیامک با این قانون مسدود شد",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("custom_filter_rule_switch_${rule.id}")
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_custom_filter_rule_${rule.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف قانون ${rule.displayTitle()}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomFilterRuleDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, pattern: String, target: FilterTarget, action: FilterAction) -> Unit
) {
    var selectedTarget by rememberSaveable { mutableStateOf(FilterTarget.BODY_KEYWORD) }
    var selectedAction by rememberSaveable { mutableStateOf(FilterAction.IGNORE_LOG_ONLY) }
    var patternInput by rememberSaveable { mutableStateOf("") }
    var nameInput by rememberSaveable { mutableStateOf("") }

    val cleanPattern = if (selectedTarget == FilterTarget.MIN_AMOUNT_TOMAN) {
        patternInput.filter { it.isDigit() }
    } else {
        patternInput.trim()
    }
    val isPatternValid = when (selectedTarget) {
        FilterTarget.MIN_AMOUNT_TOMAN -> (cleanPattern.toLongOrNull() ?: 0L) > 0L
        else -> cleanPattern.isNotEmpty()
    }

    AsisSpringModal(
        onDismissRequest = onDismiss,
        widthFraction = 0.92f,
        cornerRadiusDp = 22
    ) { triggerClose ->
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "قانون فیلتر سفارشی جدید",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "نوع شرط:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (target in FilterTarget.entries) {
                        FilterChip(
                            selected = selectedTarget == target,
                            onClick = {
                                selectedTarget = target
                                if (target == FilterTarget.MIN_AMOUNT_TOMAN) {
                                    patternInput = patternInput.filter { it.isDigit() }
                                }
                            },
                            label = { Text(target.labelFa) },
                            modifier = Modifier.testTag("filter_target_${target.storageKey}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = patternInput,
                onValueChange = { raw ->
                    patternInput = if (selectedTarget == FilterTarget.MIN_AMOUNT_TOMAN) {
                        raw.filter { it.isDigit() }
                    } else {
                        raw
                    }
                },
                label = {
                    Text(
                        when (selectedTarget) {
                            FilterTarget.MIN_AMOUNT_TOMAN -> "حداقل مبلغ واریز (تومان)"
                            FilterTarget.SENDER_NUMBER -> "سرشماره یا نام فرستنده"
                            else -> "کلمه کلیدی یا عبارت برای مسدودسازی"
                        }
                    )
                },
                placeholder = { Text(selectedTarget.hintFa) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (selectedTarget == FilterTarget.MIN_AMOUNT_TOMAN) {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Text
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_filter_pattern_input")
            )

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("عنوان قانون (اختیاری)") },
                placeholder = { Text("مثلاً: حذف پیامک‌های سود بانکی") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_filter_name_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "اقدام هنگام تطبیق:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                for (action in FilterAction.entries) {
                    FilterChip(
                        selected = selectedAction == action,
                        onClick = { selectedAction = action },
                        label = { Text(action.labelFa, maxLines = 2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("filter_action_${action.storageKey}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = { triggerClose(onDismiss) }) {
                    Text("انصراف")
                }
                Button(
                    onClick = {
                        triggerClose {
                            onSave(nameInput.trim(), cleanPattern, selectedTarget, selectedAction)
                        }
                    },
                    enabled = isPatternValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_custom_filter_rule_button")
                ) {
                    Text("ذخیره قانون")
                }
            }
        }
    }
}
