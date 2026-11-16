package com.example.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BankVisualSpec(
    val code: String,
    val gridLabelFa: String,
    val cardTitleFa: String,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val logoPrimary: Color,
    val logoSecondary: Color,
    val defaultSender: String
)

object BankVisualCatalog {

    // Exact 33 Iranian banks in the order specified by the user prompt & screenshots
    val MIRZA_BANKS: List<BankVisualSpec> = listOf(
        BankVisualSpec(
            code = "melli",
            gridLabelFa = "ملی",
            cardTitleFa = "بانک ملی ایران",
            cardGradientStart = Color(0xFF8B5E1A),
            cardGradientEnd = Color(0xFFB78628),
            logoPrimary = Color(0xFFC59B27),
            logoSecondary = Color(0xFFCF2A2A),
            defaultSender = "98600060"
        ),
        BankVisualSpec(
            code = "blu",
            gridLabelFa = "بلو بانک",
            cardTitleFa = "بلو بانک",
            cardGradientStart = Color(0xFF2563EB),
            cardGradientEnd = Color(0xFF4F8FF7),
            logoPrimary = Color(0xFF5392F7),
            logoSecondary = Color(0xFFFFFFFF),
            defaultSender = "98200084210"
        ),
        BankVisualSpec(
            code = "blu_pol",
            gridLabelFa = "بلوبانک حالت پل",
            cardTitleFa = "بلوبانک حالت پل",
            cardGradientStart = Color(0xFF1D4ED8),
            cardGradientEnd = Color(0xFF3B82F6),
            logoPrimary = Color(0xFF5392F7),
            logoSecondary = Color(0xFFDBEAFE),
            defaultSender = "98200084211"
        ),
        BankVisualSpec(
            code = "gardeshgari",
            gridLabelFa = "گردشگری",
            cardTitleFa = "بانک گردشگری",
            cardGradientStart = Color(0xFF881337),
            cardGradientEnd = Color(0xFFBE123C),
            logoPrimary = Color(0xFFB91C1C),
            logoSecondary = Color(0xFF0F766E),
            defaultSender = "982000181"
        ),
        BankVisualSpec(
            code = "keshavarzi",
            gridLabelFa = "کشاورزی",
            cardTitleFa = "بانک کشاورزی",
            cardGradientStart = Color(0xFF365314),
            cardGradientEnd = Color(0xFF4D7C0F),
            logoPrimary = Color(0xFF3F6212),
            logoSecondary = Color(0xFFA16207),
            defaultSender = "98200090"
        ),
        BankVisualSpec(
            code = "tejarat",
            gridLabelFa = "تجارت",
            cardTitleFa = "بانک تجارت",
            cardGradientStart = Color(0xFF1E3A8A),
            cardGradientEnd = Color(0xFF2563EB),
            logoPrimary = Color(0xFF1E40AF),
            logoSecondary = Color(0xFF60A5FA),
            defaultSender = "98200070"
        ),
        BankVisualSpec(
            code = "mellat",
            gridLabelFa = "ملت",
            cardTitleFa = "بانک ملت",
            cardGradientStart = Color(0xFF991B1B),
            cardGradientEnd = Color(0xFFDC2626),
            logoPrimary = Color(0xFFC81E1E),
            logoSecondary = Color(0xFFF59E0B),
            defaultSender = "98200033"
        ),
        BankVisualSpec(
            code = "saderat",
            gridLabelFa = "صادرات",
            cardTitleFa = "بانک صادرات ایران",
            cardGradientStart = Color(0xFF172554),
            cardGradientEnd = Color(0xFF1D4ED8),
            logoPrimary = Color(0xFF1E297B),
            logoSecondary = Color(0xFF3B82F6),
            defaultSender = "98200069"
        ),
        BankVisualSpec(
            code = "parsian",
            gridLabelFa = "پارسیان",
            cardTitleFa = "بانک پارسیان",
            cardGradientStart = Color(0xFF78350F),
            cardGradientEnd = Color(0xFFA16207),
            logoPrimary = Color(0xFF92400E),
            logoSecondary = Color(0xFF1F2937),
            defaultSender = "98200040"
        ),
        BankVisualSpec(
            code = "maskan",
            gridLabelFa = "مسکن",
            cardTitleFa = "بانک مسکن",
            cardGradientStart = Color(0xFF9A3412),
            cardGradientEnd = Color(0xFFEA580C),
            logoPrimary = Color(0xFFEA580C),
            logoSecondary = Color(0xFFF97316),
            defaultSender = "98200014"
        ),
        BankVisualSpec(
            code = "shahr",
            gridLabelFa = "شهر",
            cardTitleFa = "بانک شهر",
            cardGradientStart = Color(0xFF991B1B),
            cardGradientEnd = Color(0xFFE11D48),
            logoPrimary = Color(0xFFDC2626),
            logoSecondary = Color(0xFF4B5563),
            defaultSender = "98200086"
        ),
        BankVisualSpec(
            code = "resalat",
            gridLabelFa = "رسالت",
            cardTitleFa = "بانک قرض‌الحسنه رسالت",
            cardGradientStart = Color(0xFF0F766E),
            cardGradientEnd = Color(0xFF0D9488),
            logoPrimary = Color(0xFFB91C1C),
            logoSecondary = Color(0xFF0D9488),
            defaultSender = "982000069"
        ),
        BankVisualSpec(
            code = "enbank",
            gridLabelFa = "اقتصادنوین",
            cardTitleFa = "بانک اقتصاد نوین",
            cardGradientStart = Color(0xFF4C1D95),
            cardGradientEnd = Color(0xFF6D28D9),
            logoPrimary = Color(0xFF5B21B6),
            logoSecondary = Color(0xFF8B5CF6),
            defaultSender = "98200050"
        ),
        BankVisualSpec(
            code = "mehr",
            gridLabelFa = "قرض الحسنه",
            cardTitleFa = "بانک قرض الحسنه",
            cardGradientStart = Color(0xFF1B8A2E),
            cardGradientEnd = Color(0xFF28A745),
            logoPrimary = Color(0xFF1E3A8A),
            logoSecondary = Color(0xFFDC2626),
            defaultSender = "B.QMEHRIRAN"
        ),
        BankVisualSpec(
            code = "sepah",
            gridLabelFa = "سپه",
            cardTitleFa = "بانک سپه",
            cardGradientStart = Color(0xFF334155),
            cardGradientEnd = Color(0xFF475569),
            logoPrimary = Color(0xFF1E3A8A),
            logoSecondary = Color(0xFF64748B),
            defaultSender = "98200022"
        ),
        BankVisualSpec(
            code = "wepod",
            gridLabelFa = "بانکداری دیجیتال پاسارگاد",
            cardTitleFa = "بانکداری دیجیتال پاسارگاد",
            cardGradientStart = Color(0xFF92400E),
            cardGradientEnd = Color(0xFFD97706),
            logoPrimary = Color(0xFFEAB308),
            logoSecondary = Color(0xFFCA8A04),
            defaultSender = "9820008881"
        ),
        BankVisualSpec(
            code = "saman",
            gridLabelFa = "سامان",
            cardTitleFa = "بانک سامان",
            cardGradientStart = Color(0xFF0369A1),
            cardGradientEnd = Color(0xFF0284C7),
            logoPrimary = Color(0xFF1D4ED8),
            logoSecondary = Color(0xFFE11D48),
            defaultSender = "9820000"
        ),
        BankVisualSpec(
            code = "day",
            gridLabelFa = "دی",
            cardTitleFa = "بانک دی",
            cardGradientStart = Color(0xFF0F766E),
            cardGradientEnd = Color(0xFF14B8A6),
            logoPrimary = Color(0xFF0D9488),
            logoSecondary = Color(0xFF2DD4BF),
            defaultSender = "98200092"
        ),
        BankVisualSpec(
            code = "khavarmianeh",
            gridLabelFa = "خاورمیانه",
            cardTitleFa = "بانک خاورمیانه",
            cardGradientStart = Color(0xFFB45309),
            cardGradientEnd = Color(0xFFF59E0B),
            logoPrimary = Color(0xFFD97706),
            logoSecondary = Color(0xFFFBBF24),
            defaultSender = "98200058"
        ),
        BankVisualSpec(
            code = "tosee_taavon",
            gridLabelFa = "توسعه تعاون",
            cardTitleFa = "بانک توسعه تعاون",
            cardGradientStart = Color(0xFF15803D),
            cardGradientEnd = Color(0xFF22C55E),
            logoPrimary = Color(0xFF15803D),
            logoSecondary = Color(0xFF16A34A),
            defaultSender = "98200099"
        ),
        BankVisualSpec(
            code = "karafarin",
            gridLabelFa = "کارآفرین",
            cardTitleFa = "بانک کارآفرین",
            cardGradientStart = Color(0xFF115E59),
            cardGradientEnd = Color(0xFF0D9488),
            logoPrimary = Color(0xFF0F766E),
            logoSecondary = Color(0xFFD97706),
            defaultSender = "98200087"
        ),
        BankVisualSpec(
            code = "sina",
            gridLabelFa = "سینا",
            cardTitleFa = "بانک سینا",
            cardGradientStart = Color(0xFF1E40AF),
            cardGradientEnd = Color(0xFF0284C7),
            logoPrimary = Color(0xFF0284C7),
            logoSecondary = Color(0xFFF59E0B),
            defaultSender = "982000727"
        ),
        BankVisualSpec(
            code = "pasargad",
            gridLabelFa = "پاسارگاد",
            cardTitleFa = "بانک پاسارگاد",
            cardGradientStart = Color(0xFF854D0E),
            cardGradientEnd = Color(0xFFCA8A04),
            logoPrimary = Color(0xFFEAB308),
            logoSecondary = Color(0xFFA16207),
            defaultSender = "982000888"
        ),
        BankVisualSpec(
            code = "ayandeh",
            gridLabelFa = "آینده",
            cardTitleFa = "بانک آینده",
            cardGradientStart = Color(0xFF713F12),
            cardGradientEnd = Color(0xFFA16207),
            logoPrimary = Color(0xFF78350F),
            logoSecondary = Color(0xFFEAB308),
            defaultSender = "98200049"
        ),
        BankVisualSpec(
            code = "sarmayeh",
            gridLabelFa = "سرمایه",
            cardTitleFa = "بانک سرمایه",
            cardGradientStart = Color(0xFF374151),
            cardGradientEnd = Color(0xFF4B5563),
            logoPrimary = Color(0xFF1E293B),
            logoSecondary = Color(0xFF94A3B8),
            defaultSender = "98200075"
        ),
        BankVisualSpec(
            code = "iranzamin",
            gridLabelFa = "ایران زمین",
            cardTitleFa = "بانک ایران زمین",
            cardGradientStart = Color(0xFF581C87),
            cardGradientEnd = Color(0xFF7E22CE),
            logoPrimary = Color(0xFF6B21A8),
            logoSecondary = Color(0xFF0D9488),
            defaultSender = "98200068"
        ),
        BankVisualSpec(
            code = "postbank",
            gridLabelFa = "پست بانک ایران",
            cardTitleFa = "پست بانک ایران",
            cardGradientStart = Color(0xFF14532D),
            cardGradientEnd = Color(0xFF16A34A),
            logoPrimary = Color(0xFF15803D),
            logoSecondary = Color(0xFF4ADE80),
            defaultSender = "98200084"
        ),
        BankVisualSpec(
            code = "refah",
            gridLabelFa = "رفاه کارگران",
            cardTitleFa = "بانک رفاه کارگران",
            cardGradientStart = Color(0xFF1E3A8A),
            cardGradientEnd = Color(0xFF3B82F6),
            logoPrimary = Color(0xFF1E3A8A),
            logoSecondary = Color(0xFF2563EB),
            defaultSender = "98200000"
        ),
        BankVisualSpec(
            code = "edbi",
            gridLabelFa = "توسعه صادرات",
            cardTitleFa = "بانک توسعه صادرات",
            cardGradientStart = Color(0xFF065F46),
            cardGradientEnd = Color(0xFF10B981),
            logoPrimary = Color(0xFF15803D),
            logoSecondary = Color(0xFF34D399),
            defaultSender = "98200027"
        ),
        BankVisualSpec(
            code = "sanat_madan",
            gridLabelFa = "صنعت و معدن",
            cardTitleFa = "بانک صنعت و معدن",
            cardGradientStart = Color(0xFF78350F),
            cardGradientEnd = Color(0xFFB45309),
            logoPrimary = Color(0xFF92400E),
            logoSecondary = Color(0xFFD97706),
            defaultSender = "98200011"
        ),
        BankVisualSpec(
            code = "kosar",
            gridLabelFa = "موسسه اعتباری کوثر",
            cardTitleFa = "موسسه اعتباری کوثر",
            cardGradientStart = Color(0xFF831843),
            cardGradientEnd = Color(0xFFBE185D),
            logoPrimary = Color(0xFF9D174D),
            logoSecondary = Color(0xFFF59E0B),
            defaultSender = "98200044"
        ),
        BankVisualSpec(
            code = "melal",
            gridLabelFa = "موسسه اعتباری ملل",
            cardTitleFa = "موسسه اعتباری ملل",
            cardGradientStart = Color(0xFF312E81),
            cardGradientEnd = Color(0xFF4F46E5),
            logoPrimary = Color(0xFF3730A3),
            logoSecondary = Color(0xFFEA580C),
            defaultSender = "982000755"
        ),
        BankVisualSpec(
            code = "noor",
            gridLabelFa = "موسسه اعتباری نور",
            cardTitleFa = "موسسه اعتباری نور",
            cardGradientStart = Color(0xFF0C4A6E),
            cardGradientEnd = Color(0xFF0284C7),
            logoPrimary = Color(0xFF0369A1),
            logoSecondary = Color(0xFF2DD4BF),
            defaultSender = "98200095"
        )
    )

    fun findByCode(code: String?): BankVisualSpec? {
        if (code.isNullOrBlank()) return null
        return MIRZA_BANKS.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }

    fun findByCodeOrTitle(codeOrSender: String?, bankTitle: String?): BankVisualSpec? {
        findByCode(codeOrSender)?.let { return it }
        val detected = com.example.data.SmsParser.detectBank(codeOrSender, bankTitle)
        findByCode(detected.code)?.let { return it }
        if (!bankTitle.isNullOrBlank()) {
            MIRZA_BANKS.firstOrNull {
                bankTitle.contains(it.gridLabelFa) || it.cardTitleFa.contains(bankTitle)
            }?.let { return it }
        }
        return null
    }
}

/**
 * Circular white badge containing each Iranian bank's distinctive geometric emblem
 * matching Screenshots 2 & 3.
 */
@Composable
fun BankLogoBadge(
    spec: BankVisualSpec?,
    size: Dp = 54.dp,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (isSelected) {
                    Modifier
                        .border(2.5.dp, Color(0xFF38D373), CircleShape)
                        .padding(3.dp)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (spec == null) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = "انتخاب بانک",
                tint = Color(0xFF56636A),
                modifier = Modifier.size(size * 0.54f)
            )
        } else {
            when (spec.code) {
                "blu", "blu_pol" -> {
                    Box(
                        modifier = Modifier
                            .size(size * 0.68f)
                            .clip(RoundedCornerShape(size * 0.18f))
                            .background(spec.logoPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "b",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (size.value * 0.38f).sp
                        )
                    }
                }
                "resalat" -> {
                    Text(
                        text = "رسالت",
                        color = spec.logoPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.24f).sp
                    )
                }
                else -> {
                    Canvas(modifier = Modifier.size(size * 0.68f)) {
                        val w = this.size.width
                        val h = this.size.height
                        val cx = w / 2f
                        val cy = h / 2f
                        val r = minOf(w, h) / 2f

                        when (spec.code) {
                            "mellat" -> {
                                for (angle in listOf(0f, 120f, 240f)) {
                                    rotate(angle, pivot = Offset(cx, cy)) {
                                        drawRoundRect(
                                            color = spec.logoPrimary,
                                            topLeft = Offset(cx - r * 0.28f, cy - r * 0.85f),
                                            size = Size(r * 0.56f, r * 0.56f),
                                            cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
                                        )
                                    }
                                }
                                drawCircle(
                                    color = spec.logoSecondary,
                                    radius = r * 0.26f,
                                    center = Offset(cx, cy)
                                )
                            }
                            "saderat", "edbi" -> {
                                val colW = r * 0.45f
                                drawRoundRect(
                                    color = spec.logoPrimary,
                                    topLeft = Offset(cx - r * 0.72f, cy - r * 0.75f),
                                    size = Size(colW, r * 1.5f),
                                    cornerRadius = CornerRadius(colW / 2f, colW / 2f)
                                )
                                drawRoundRect(
                                    color = spec.logoPrimary,
                                    topLeft = Offset(cx + r * 0.27f, cy - r * 0.75f),
                                    size = Size(colW, r * 1.5f),
                                    cornerRadius = CornerRadius(colW / 2f, colW / 2f)
                                )
                                drawCircle(
                                    color = spec.logoPrimary,
                                    radius = r * 0.24f,
                                    center = Offset(cx, cy)
                                )
                            }
                            "maskan" -> {
                                drawRect(
                                    color = spec.logoPrimary,
                                    topLeft = Offset(cx - r * 0.68f, cy - r * 0.68f),
                                    size = Size(r * 1.36f, r * 1.36f),
                                    style = Stroke(width = r * 0.22f)
                                )
                                val roof = Path().apply {
                                    moveTo(cx - r * 0.55f, cy + r * 0.25f)
                                    lineTo(cx, cy - r * 0.2f)
                                    lineTo(cx + r * 0.55f, cy + r * 0.25f)
                                }
                                drawPath(roof, color = spec.logoPrimary, style = Stroke(width = r * 0.20f))
                            }
                            "saman", "mehr" -> {
                                for (i in 0 until 5) {
                                    rotate(i * 72f, pivot = Offset(cx, cy)) {
                                        drawCircle(
                                            color = spec.logoPrimary,
                                            radius = r * 0.34f,
                                            center = Offset(cx, cy - r * 0.44f),
                                            style = Stroke(width = r * 0.16f)
                                        )
                                    }
                                }
                                drawCircle(
                                    color = spec.logoSecondary,
                                    radius = r * 0.26f,
                                    center = Offset(cx, cy)
                                )
                            }
                            "enbank" -> {
                                drawLine(
                                    color = spec.logoPrimary,
                                    start = Offset(cx - r * 0.45f, cy + r * 0.7f),
                                    end = Offset(cx + r * 0.05f, cy - r * 0.7f),
                                    strokeWidth = r * 0.22f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = spec.logoSecondary,
                                    start = Offset(cx - r * 0.05f, cy + r * 0.7f),
                                    end = Offset(cx + r * 0.45f, cy - r * 0.7f),
                                    strokeWidth = r * 0.22f,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = spec.logoPrimary,
                                    radius = r * 0.16f,
                                    center = Offset(cx - r * 0.55f, cy - r * 0.35f)
                                )
                                drawCircle(
                                    color = spec.logoPrimary,
                                    radius = r * 0.16f,
                                    center = Offset(cx + r * 0.55f, cy + r * 0.35f)
                                )
                            }
                            "day" -> {
                                drawCircle(
                                    color = spec.logoPrimary,
                                    radius = r * 0.38f,
                                    center = Offset(cx - r * 0.36f, cy),
                                    style = Stroke(width = r * 0.20f)
                                )
                                drawCircle(
                                    color = spec.logoPrimary,
                                    radius = r * 0.38f,
                                    center = Offset(cx + r * 0.36f, cy),
                                    style = Stroke(width = r * 0.20f)
                                )
                            }
                            "pasargad", "wepod" -> {
                                val path = Path().apply {
                                    moveTo(cx - r * 0.45f, cy - r * 0.75f)
                                    lineTo(cx + r * 0.15f, cy - r * 0.75f)
                                    lineTo(cx + r * 0.05f, cy + r * 0.55f)
                                    lineTo(cx + r * 0.55f, cy + r * 0.55f)
                                    lineTo(cx + r * 0.55f, cy + r * 0.75f)
                                    lineTo(cx - r * 0.35f, cy + r * 0.75f)
                                    close()
                                }
                                drawPath(path, color = spec.logoPrimary)
                            }
                            "gardeshgari" -> {
                                for (i in 0 until 6) {
                                    rotate(i * 60f, pivot = Offset(cx, cy)) {
                                        drawLine(
                                            color = spec.logoPrimary,
                                            start = Offset(cx - r * 0.3f, cy - r * 0.52f),
                                            end = Offset(cx + r * 0.35f, cy - r * 0.52f),
                                            strokeWidth = r * 0.18f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                                drawCircle(
                                    color = spec.logoSecondary,
                                    radius = r * 0.20f,
                                    center = Offset(cx, cy)
                                )
                            }
                            "khavarmianeh" -> {
                                for (i in 0 until 12) {
                                    rotate(i * 30f, pivot = Offset(cx, cy)) {
                                        drawLine(
                                            color = spec.logoPrimary,
                                            start = Offset(cx, cy - r * 0.32f),
                                            end = Offset(cx, cy - r * 0.85f),
                                            strokeWidth = r * 0.14f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            "ayandeh" -> {
                                drawRoundRect(
                                    color = spec.logoPrimary,
                                    topLeft = Offset(cx - r * 0.72f, cy - r * 0.72f),
                                    size = Size(r * 1.44f, r * 1.44f),
                                    cornerRadius = CornerRadius(r * 0.25f, r * 0.25f)
                                )
                                drawLine(
                                    color = spec.logoSecondary,
                                    start = Offset(cx - r * 0.55f, cy - r * 0.1f),
                                    end = Offset(cx + r * 0.55f, cy + r * 0.1f),
                                    strokeWidth = r * 0.26f,
                                    cap = StrokeCap.Round
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = spec.logoPrimary,
                                    radius = r * 0.72f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = r * 0.22f)
                                )
                                drawCircle(
                                    color = spec.logoSecondary,
                                    radius = r * 0.32f,
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Realistic debit/credit bank card used on both:
 * 1. "بانک‌های تعریف شده" (with delete button on bottom-left)
 * 2. "افزودن بانک جدید" (live interactive preview card at the top)
 */
@Composable
fun RealisticBankCard(
    spec: BankVisualSpec?,
    bankTitleOverride: String? = null,
    senderNumber: String,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val startColor = spec?.cardGradientStart ?: Color(0xFF4B5558)
    val endColor = spec?.cardGradientEnd ?: Color(0xFF343D42)
    val displayTitle = bankTitleOverride?.takeIf { it.isNotBlank() }
        ?: spec?.cardTitleFa
        ?: "بانک را انتخاب کنید"
    val displaySender = senderNumber.trim().takeIf { it.isNotEmpty() } ?: "••••••••"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(198.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(startColor, endColor),
                    start = Offset(0f, 0f),
                    end = Offset(950f, 520f)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // Decorative translucent curved wave arcs & subtle diagonal sheen matching Pro Max card finish
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.085f),
                radius = size.height * 0.88f,
                center = Offset(size.width * 0.88f, size.height * 0.08f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.height * 0.74f,
                center = Offset(size.width * 0.10f, size.height * 0.98f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.20f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.22f, size.height * 0.20f)
            )
        }

        // Top Row: Bank Logo Badge + Bank Title on the right (in RTL, start is right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            BankLogoBadge(
                spec = spec,
                size = 50.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (spec != null) "متصل به شنودگر هوشمند پیامک" else "پیش‌نمایش زنده کارت بانکی",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }

        // Center-Left: Golden EMV Chip (in RTL, Alignment.CenterEnd is physical left)
        GoldenEmvChip(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp, bottom = 6.dp)
        )

        // Bottom Row: "سرشماره پیامک" + sender ID on the right (start), and optional Delete button on the left (end)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "سرشماره پیامک",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    text = displaySender,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (senderNumber.isBlank()) 3.sp else 0.8.sp,
                        textDirection = TextDirection.Ltr
                    ),
                    color = Color.White
                )
            }

            if (onDeleteClick != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.28f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDeleteClick)
                        .testTag("delete_bank_button_$displaySender")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "حذف بانک",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoldenEmvChip(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(46.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
    ) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF6D365),
                    Color(0xFFD4AF37),
                    Color(0xFFB8860B)
                )
            ),
            cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
        )
        val lineColor = Color(0xFF785109).copy(alpha = 0.55f)
        val stroke = 1.2.dp.toPx()
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height * 0.35f),
            end = Offset(size.width, size.height * 0.35f),
            strokeWidth = stroke
        )
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height * 0.65f),
            end = Offset(size.width, size.height * 0.65f),
            strokeWidth = stroke
        )
        drawLine(
            color = lineColor,
            start = Offset(size.width * 0.34f, 0f),
            end = Offset(size.width * 0.34f, size.height),
            strokeWidth = stroke
        )
        drawLine(
            color = lineColor,
            start = Offset(size.width * 0.66f, 0f),
            end = Offset(size.width * 0.66f, size.height),
            strokeWidth = stroke
        )
    }
}
