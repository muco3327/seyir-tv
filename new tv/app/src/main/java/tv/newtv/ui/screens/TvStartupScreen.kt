package tv.newtv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TvStartupScreen(message: String, progress: Pair<Int, Int>) {
    // Sade beliriş (fade-in) animasyonu
    val infiniteTransition = rememberInfiniteTransition(label = "fade")
    val fadeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fadeAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp).alpha(fadeAlpha)
        ) {
            // ================= LOGO / AMBLEM (Minimal N) =================
            Text(
                text = "N",
                color = Color(0xFFE50914),
                fontSize = 110.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            // ================= BAŞLIK =================
            Text(
                text = "NEW TV",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(48.dp))

            // ================= İLERLEME ÇUBUĞU =================
            val progressRatio = if (progress.second > 0) {
                (progress.first.toFloat() / progress.second.toFloat()).coerceIn(0f, 1f)
            } else -1f

            if (progressRatio >= 0f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(260.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFE50914),
                        trackColor = Color(0xFF2B2B2B),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${progress.first} / ${progress.second} hazır",
                        color = Color(0xFF808080),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0xFF2B2B2B))
                ) {
                    // Sade bir indeterminate görünüm veya boş bırakılabilir
                }
            }

            Spacer(Modifier.height(24.dp))

            // ================= BİLGİ ETİKETİ =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914))
                )
                Text(
                    text = message.ifBlank { "Başlatılıyor..." },
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
