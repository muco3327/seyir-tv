package tv.newtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.newtv.data.local.ChannelEntity
import tv.newtv.repository.IptvRepository

@Composable
fun TvChannelSourceDialog(
    channel: ChannelEntity,
    repository: IptvRepository,
    onDismiss: () -> Unit,
    onSourceSelected: (sourceIndex: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sources = remember(channel) { channel.getStreamUrls() }

    var isTestingAll by remember { mutableStateOf(false) }
    val testResults = remember { mutableStateMapOf<Int, Pair<Boolean, Long>>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(680.dp)
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .padding(22.dp)
            ) {
                // Başlık
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${sources.size} Yedek Yayın Kaynağı Mevcut",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Kaynak Listesi
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(sources) { index, url ->
                        val itemInteraction = remember { MutableInteractionSource() }
                        val isItemFocused by itemInteraction.collectIsFocusedAsState()
                        val testResult = testResults[index]

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isItemFocused) 1.02f else 1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isItemFocused) Color(0xFF1E293B) else Color(0xFF151E32))
                                .border(
                                    width = if (isItemFocused) 2.dp else 1.dp,
                                    color = if (isItemFocused) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusable(interactionSource = itemInteraction)
                                .clickable {
                                    onSourceSelected(index)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isItemFocused) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Kaynak ${index + 1}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = url.take(45) + if (url.length > 45) "..." else "",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Test Sonucu Rozeti
                                if (testResult != null) {
                                    val isOnline = testResult.first
                                    val latency = testResult.second
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isOnline) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isOnline) "ONLINE (${latency}ms)" else "OFFLINE",
                                            color = if (isOnline) Color(0xFF34D399) else Color(0xFFF87171),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Alt Buton: Tüm Kaynakları Test Et
                val testBtnInteraction = remember { MutableInteractionSource() }
                val isTestFocused by testBtnInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(if (isTestFocused) 1.02f else 1.0f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTestFocused) Color(0xFF0284C7) else Color(0xFF0369A1))
                        .border(
                            width = if (isTestFocused) 2.dp else 0.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusable(interactionSource = testBtnInteraction)
                        .clickable(enabled = !isTestingAll) {
                            isTestingAll = true
                            coroutineScope.launch {
                                val results = repository.testChannelAllSources(channel)
                                results.forEachIndexed { i, pair ->
                                    testResults[i] = pair.second
                                }
                                isTestingAll = false
                            }
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTestingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isTestingAll) "Kaynaklar Test Ediliyor..." else "Tüm Kaynakları Test Et",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
