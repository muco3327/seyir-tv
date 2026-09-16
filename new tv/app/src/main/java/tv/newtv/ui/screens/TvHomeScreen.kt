package tv.newtv.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvHomeScreen(
    onOpenIptv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGitHubScanner: () -> Unit,
    onExit: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Canlı saat ve tarih akışı
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
        while (true) {
            currentTimeString = formatter.format(Date())
            delay(1000)
        }
    }

    // İlk odak: "CANLI TV" kartı
    val liveTvFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { liveTvFocusRequester.requestFocus() }
    }

    // Ana ekrandayken geri tuşu çıkış onayı sunsun
    BackHandler {
        showExitDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {


        // VIP Kurdele kaldırıldı

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ================= ÜST BAR (HEADER) =================
            TvHomeHeader(
                timeString = currentTimeString,
                onOpenFavorites = onOpenHistory,
                onOpenIptv = onOpenIptv,
                onOpenHistory = onOpenHistory,
                onOpenGitHubScanner = onOpenGitHubScanner,
                onOpenSettings = onOpenSettings,
                onExitClick = { showExitDialog = true },
                onInfoClick = { showInfoDialog = true }
            )

            // ================= 3 KAHRAMAN KART (30% KÜÇÜLTÜLMÜŞ & MODERN CAM EFEKTLİ) =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.65f) // %30 daha küçük
                        .fillMaxHeight(0.42f),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. CANLI TV (Neon Turkuaz)
                    TvHeroMenuCard(
                        title = "CANLI TV",
                        subtitle = "Spor & Ulusal Kanallar",
                        badgeText = "CANLI",
                        accentColor = Color(0xFF00F2FE),
                        glowColor = Color(0xFF00D2A0),
                        gradientColors = listOf(
                            Color(0xFF0C2C33),
                            Color(0xFF091E23),
                            Color(0xFF061217)
                        ),
                        icon = { TvDeviceIcon(accentColor = Color(0xFF00F2FE)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(liveTvFocusRequester),
                        onClick = onOpenIptv
                    )

                    // 2. FİLMLER (Neon Kırmızı-Turuncu)
                    TvHeroMenuCard(
                        title = "FİLMLER",
                        subtitle = "Yerli & Yabancı Sinema",
                        badgeText = "SİNEMA",
                        accentColor = Color(0xFFFF2A54),
                        glowColor = Color(0xFFFF6B00),
                        gradientColors = listOf(
                            Color(0xFF380E18),
                            Color(0xFF260A11),
                            Color(0xFF140509)
                        ),
                        icon = { MovieDeviceIcon(accentColor = Color(0xFFFF2A54)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onOpenMovies
                    )

                    // 3. DİZİLER (Neon Mor-İndigo)
                    TvHeroMenuCard(
                        title = "DİZİLER",
                        subtitle = "Dizilla & Sezonluk Diziler",
                        badgeText = "DİZİ ARŞİVİ",
                        accentColor = Color(0xFFA855F7),
                        glowColor = Color(0xFF7C3AED),
                        gradientColors = listOf(
                            Color(0xFF280E42),
                            Color(0xFF1A092C),
                            Color(0xFF10051C)
                        ),
                        icon = { SeriesDeviceIcon(accentColor = Color(0xFFA855F7)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = onOpenSeries
                    )
                }
            }

            // Alt Kumanda Bilgi İpuçları
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvRemoteKeyHint(keyLabel = "OK", actionLabel = "Giriş Yap")
                Spacer(Modifier.width(24.dp))
                TvRemoteKeyHint(keyLabel = "◀  ▶", actionLabel = "Kategoriler Arası Geçiş")
                Spacer(Modifier.width(24.dp))
                TvRemoteKeyHint(keyLabel = "▲", actionLabel = "Hızlı İşlemler")
            }
        }
    }

    // Çıkış Diyaloğu
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "Uygulamadan Çıkılsın mı?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "New TV uygulamasını kapatmak istediğinizden emin misiniz?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExit()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4D4D))
                ) {
                    Text("Çıkış Yap", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("İptal")
                }
            },
            containerColor = Color(0xFF1E2430),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Bilgi Diyaloğu
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "New TV / Seyir TV",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Sürüm: 2.0 (Android TV Dashboard Edition)",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "• Canlı TV yayınları başlangıçta hazırlandı.\n• Filmler ve Diziler seçildiğinde kendi canlı taramasını başlatır.\n• Yüksek hızlı HLS & MP4 donanım hızlandırmalı oynatıcı.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text("Tamam", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E2430),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ================= ÜST BAR (HEADER BİLEŞENİ) =================
@Composable
private fun TvHomeHeader(
    timeString: String,
    onOpenFavorites: () -> Unit,
    onOpenIptv: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGitHubScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol Bölüm: Logo Rozeti + Saat & Tarih
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Minimalist Logo (Netflix Style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "N",
                    color = Color(0xFFE50914),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
                Text(
                    text = "EW TV",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }

            if (timeString.isNotBlank()) {
                Text(
                    text = timeString,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Sağ Taraf: Hızlı İşlem Dairesel İkonları
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvHeaderIconButton(icon = Icons.Filled.Star, contentDescription = "Favoriler", onClick = onOpenFavorites)
            TvHeaderIconButton(icon = Icons.Filled.Tv, contentDescription = "Canlı TV", onClick = onOpenIptv)
            TvHeaderIconButton(icon = Icons.Filled.History, contentDescription = "Geçmiş", onClick = onOpenHistory)
            TvHeaderIconButton(icon = Icons.Filled.CloudDownload, contentDescription = "GitHub Tara", onClick = onOpenGitHubScanner)
            TvHeaderIconButton(icon = Icons.Filled.Settings, contentDescription = "Ayarlar", onClick = onOpenSettings)
            TvHeaderIconButton(icon = Icons.Filled.PowerSettingsNew, contentDescription = "Çıkış", onClick = onExitClick)
            TvHeaderIconButton(icon = Icons.Filled.Info, contentDescription = "Bilgi", onClick = onInfoClick)
            Spacer(Modifier.width(36.dp)) // Kurdele için boşluk
        }
    }
}

// ================= HAP BUTON (AI ARAMA / SPOR) =================
@Composable
private fun TvHeaderPillButton(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "pillScale")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) Brush.horizontalGradient(listOf(Color.White, Color(0xFFE2E8F0))) else gradient)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ================= DAİRESEL ÜST BAR İKON BUTONU =================
@Composable
private fun TvHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(if (isFocused) 1.16f else 1.0f, label = "iconScale")

    Box(
        modifier = Modifier
            .size(38.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color(0xFF1E293B).copy(alpha = 0.7f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF475569).copy(alpha = 0.6f),
                shape = CircleShape
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color(0xFFE2E8F0),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ================= 3 ANA KAHRAMAN KART BİLEŞENİ (GÖZ ALICI CAM TASARIM) =================
@Composable
private fun TvHeroMenuCard(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    glowColor: Color,
    gradientColors: List<Color>,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "heroCardScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2B2B2B) else Color(0xFF1A1A1A))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF333333),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
    ) {

        // Sağ Üst Köşe: Şık Rozet (Badge)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isFocused) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = if (isFocused) accentColor.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (badgeText == "CANLI") {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FF87))
                    )
                }
                Text(
                    text = badgeText,
                    color = if (isFocused) Color.White else Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Kart İçeriği (İkon Haznesi + Başlık + Alt Başlık)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Minimal İkon
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(Modifier.height(14.dp))

            // Başlık
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(4.dp))

            // Alt Başlık
            Text(
                text = subtitle,
                color = if (isFocused) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )

            // Odaklandığında parlayan alt etiket
            if (isFocused) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = 0.20f))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "BAŞLAT ▶",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

// ================= AYGIR İKONLARI (ŞIK, MODERN CAM TASARIMLI) =================

@Composable
private fun TvDeviceIcon(accentColor: Color = Color(0xFF00F2FE)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        TunerSliderControl(width = 30.dp, accentColor = accentColor)
    }
}

@Composable
private fun MovieDeviceIcon(accentColor: Color = Color(0xFFFF2A54)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        TunerSliderControl(width = 30.dp, accentColor = accentColor)
    }
}

@Composable
private fun SeriesDeviceIcon(accentColor: Color = Color(0xFFA855F7)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        TunerSliderControl(width = 30.dp, accentColor = accentColor)
    }
}

@Composable
private fun TunerSliderControl(width: androidx.compose.ui.unit.Dp, accentColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .width(width)
            .height(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 6.dp)
                .width(3.dp)
                .height(7.dp)
                .background(accentColor, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun TvRemoteKeyHint(keyLabel: String, actionLabel: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = keyLabel,
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = actionLabel,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ================= ALTIN VIP KURDELE SÜSLEMESİ =================
@Composable
private fun GoldenRibbonBadge(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = 44.dp, height = 56.dp)
    ) {
        val ribbonPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height - 14f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = ribbonPath,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFFDF73),
                    Color(0xFFD4AF37),
                    Color(0xFFAA7C11),
                    Color(0xFF805A08)
                )
            )
        )
    }
}
