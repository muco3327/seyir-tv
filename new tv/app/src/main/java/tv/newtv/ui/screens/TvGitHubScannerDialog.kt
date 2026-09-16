package tv.newtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.newtv.data.local.IptvSettingsManager
import tv.newtv.network.DiscoveredPlaylist
import tv.newtv.network.GitHubPlaylistScanner

@Composable
fun TvGitHubScannerDialog(
    onDismiss: () -> Unit,
    onPlaylistsAdded: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { GitHubPlaylistScanner() }

    var isScanning by remember { mutableStateOf(false) }
    var scanStatusMessage by remember { mutableStateOf("GitHub'dan en güncel Türk IPTV listelerini aramak için 'Taramayı Başlat' butonuna basın.") }
    var discoveredList by remember { mutableStateOf<List<DiscoveredPlaylist>>(emptyList()) }
    val selectedUrls = remember { mutableStateListOf<String>() }

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
                    .width(820.dp)
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                // Başlık
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🌐", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GitHub Canlı M3U Keşfi",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
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

                Spacer(modifier = Modifier.height(14.dp))

                // Bilgilendirme / Durum Kartı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = scanStatusMessage,
                            color = if (isScanning) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bulunan Listeler
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (discoveredList.isEmpty() && !isScanning) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Henüz keşfedilmiş liste yok. Aramayı başlatın.",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredList) { item ->
                                val isSelected = selectedUrls.contains(item.url)
                                val itemInteraction = remember { MutableInteractionSource() }
                                val isItemFocused by itemInteraction.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(if (isItemFocused) 1.02f else 1.0f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                                        .border(
                                            width = if (isItemFocused) 2.dp else 1.dp,
                                            color = if (isItemFocused) Color.White else if (isSelected) Color(0xFF3B82F6) else Color(0xFF334155),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .focusable(interactionSource = itemInteraction)
                                        .clickable {
                                            if (isSelected) selectedUrls.remove(item.url)
                                            else selectedUrls.add(item.url)
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.fileName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${item.repo} • Güncelleme: ${item.lastUpdated}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${item.estimatedChannels}+ Kanal",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Alt Butonlar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Taramayı Başlat Butonu
                    val scanInteraction = remember { MutableInteractionSource() }
                    val isScanFocused by scanInteraction.collectIsFocusedAsState()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (isScanFocused) 1.03f else 1.0f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isScanFocused) Color(0xFF2563EB) else Color(0xFF1D4ED8))
                            .border(
                                width = if (isScanFocused) 2.dp else 0.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusable(interactionSource = scanInteraction)
                            .clickable(enabled = !isScanning) {
                                isScanning = true
                                discoveredList = emptyList()
                                selectedUrls.clear()
                                coroutineScope.launch {
                                    val results = scanner.scanPlaylists { _, _, msg ->
                                        scanStatusMessage = msg
                                    }
                                    discoveredList = results
                                    selectedUrls.addAll(results.map { it.url })
                                    isScanning = false
                                    scanStatusMessage = if (results.isNotEmpty()) {
                                        "${results.size} liste bulundu. Yalnızca izin verilen spor, ulusal ve haber kanalları kaydediliyor."
                                    } else {
                                        "Güncel liste bulunamadı veya ağ bağlantısı zaman aşımına uğradı."
                                    }
                                    if (results.isNotEmpty()) {
                                        val urls = results.map { it.url }
                                        IptvSettingsManager.addCustomSources(context, urls)
                                        onPlaylistsAdded(urls)
                                        onDismiss()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isScanning) "Taranıyor..." else "GitHub'ı Canlı Tara",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Seçilenleri Ekle Butonu
                    if (discoveredList.isNotEmpty()) {
                        val addInteraction = remember { MutableInteractionSource() }
                        val isAddFocused by addInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(if (isAddFocused) 1.03f else 1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAddFocused) Color(0xFF059669) else Color(0xFF10B981))
                                .border(
                                    width = if (isAddFocused) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusable(interactionSource = addInteraction)
                                .clickable {
                                    val urls = selectedUrls.toList()
                                    if (urls.isNotEmpty()) {
                                        IptvSettingsManager.addCustomSources(context, urls)
                                        onPlaylistsAdded(urls)
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Listeleri İçe Aktar (${selectedUrls.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
