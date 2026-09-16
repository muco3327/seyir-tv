package tv.newtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.newtv.data.local.ChannelEntity
import tv.newtv.data.local.ChannelStatus
import tv.newtv.ui.viewmodel.ChannelFilterType
import tv.newtv.ui.viewmodel.TvMainViewModel

fun detectChannelQuality(name: String): Pair<String, Color> {
    val upper = name.uppercase()
    return when {
        upper.contains("4K") || upper.contains("UHD") || upper.contains("2160P") -> Pair("4K", Color(0xFFEF4444))
        upper.contains("FHD") || upper.contains("1080P") || upper.contains("FULL HD") -> Pair("FHD", Color(0xFFF59E0B))
        upper.contains("HD") || upper.contains("720P") -> Pair("HD", Color(0xFF10B981))
        else -> Pair("SD", Color(0xFF94A3B8))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvIptvScreen(
    viewModel: TvMainViewModel,
    onChannelClick: (channel: ChannelEntity, sourceIndex: Int) -> Unit,
    onBackToHome: () -> Unit = {}
) {
    androidx.activity.compose.BackHandler {
        onBackToHome()
    }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels by viewModel.displayChannels.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val channelStatuses by viewModel.channelStatuses.collectAsStateWithLifecycle()
    val channelFilter by viewModel.channelFilter.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val offlineCount by viewModel.offlineCount.collectAsStateWithLifecycle()
    val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
    val isRefreshingPlaylists by viewModel.isRefreshingPlaylists.collectAsStateWithLifecycle()
    val verificationProgress by viewModel.verificationProgress.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playlistError by viewModel.playlistError.collectAsStateWithLifecycle()

    var showGitHubDialog by remember { mutableStateOf(false) }
    var selectedChannelForSources by remember { mutableStateOf<ChannelEntity?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val restoreChannelId = remember { viewModel.lastFocusedChannelId }
    var restoredChannel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // En Üst Başlık & Arama Çubuğu (Üste Sıfır)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvVodHomeBackButton(onClick = onBackToHome)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0284C7), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CANLI TV",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (searchQuery.isNotBlank()) "Arama: \"$searchQuery\"" else (selectedCategory ?: "Tüm Kanallar"),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF222632), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF333A4C), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "${channels.size} Eşleşen Kanal" else "$totalCount Kanal",
                        color = if (searchQuery.isNotBlank()) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Android TV Uyumlu Arama Çubuğu (En Üste Sıfır)
            TvSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onSearch = { },
                onClear = { viewModel.setSearchQuery("") },
                onExitToResults = { },
                placeholder = "Kanal ara..."
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            // Sol Sütun: Modern Kategori & Araçlar Menüsü (230dp)
            Column(
                modifier = Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "CANLI KANALLAR",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                // 1. SPOR KANALLARI
                val isSportsSelected = selectedSource == TvMainViewModel.VIRTUAL_SPORTS_SOURCE
                item(key = "sports-source") {
                    TvSourceItem(
                        title = "🏆 Spor Kanalları",
                        isSelected = isSportsSelected,
                        onClick = {
                            viewModel.setSearchQuery("")
                            viewModel.selectSource(TvMainViewModel.VIRTUAL_SPORTS_SOURCE)
                        }
                    )
                }
                if (isSportsSelected) {
                    items(categories, key = { "sports-category:$it" }) { category ->
                        TvCategorySubItem(
                            title = category,
                            isSelected = selectedCategory == category,
                            onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.selectCategory(category)
                            }
                        )
                    }
                }

                item(key = "source-spacer") { Spacer(modifier = Modifier.height(6.dp)) }

                // 2. ULUSAL KANALLAR
                val isNationalSelected = selectedSource == TvMainViewModel.VIRTUAL_NATIONAL_SOURCE
                item(key = "national-source") {
                    TvSourceItem(
                        title = "🇹🇷 Ulusal Kanallar",
                        isSelected = isNationalSelected,
                        onClick = {
                            viewModel.setSearchQuery("")
                            viewModel.selectSource(TvMainViewModel.VIRTUAL_NATIONAL_SOURCE)
                        }
                    )
                }
                if (isNationalSelected) {
                    items(categories, key = { "national-category:$it" }) { category ->
                        TvCategorySubItem(
                            title = category,
                            isSelected = selectedCategory == category,
                            onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.selectCategory(category)
                            }
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .height(1.dp)
                            .background(Color(0xFF1E2430))
                    )
                }

                // ARAÇLAR VE ÖZELLİKLER BÖLÜMÜ
                item {
                    Text(
                        text = "ARAÇLAR & AYARLAR",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }

                // A. Kanal Ara
                item {
                    TvToolMenuItem(
                        title = if (searchQuery.isNotBlank()) "Arama: \"$searchQuery\"" else "🔍 Kanal Ara...",
                        color = Color(0xFF38BDF8),
                        onClick = { showSearchDialog = true }
                    )
                }

                // B. GitHub Canlı M3U Keşfi
                item {
                    TvToolMenuItem(
                        title = "🌐 GitHub M3U Keşfi",
                        color = Color(0xFFFBBF24),
                        onClick = { showGitHubDialog = true }
                    )
                }

                // C. Kanalları Doğrula
                item {
                    TvToolMenuItem(
                        title = if (isVerifying) "⚡ Testi Durdur (${verificationProgress.first}/${verificationProgress.second})" else "⚡ Kanalları Test Et",
                        color = if (isVerifying) Color(0xFFEF4444) else Color(0xFF10B981),
                        onClick = {
                            if (isVerifying) viewModel.cancelVerification() else viewModel.startVerification(force = true)
                        }
                    )
                }

                // E. Listeleri Yenile
                item {
                    TvToolMenuItem(
                        title = if (isRefreshingPlaylists) "🔄 Yenileniyor..." else "🔄 Listeleri Yenile",
                        color = Color(0xFFFB923C),
                        onClick = { viewModel.refreshPlaylists() }
                    )
                }
            }
        }

        // Ayırıcı İnce Dikey Çizgi
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 14.dp)
                .background(Color(0xFF1E222B))
        )

        // Sağ Alan: Başlık, Filtreler ve Kanal Izgarası
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
        ) {
            // Doğrulama Durumu ve Filtre Çipleri
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp, start = 4.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test ediliyor: ${verificationProgress.first}/${verificationProgress.second}",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    } else if (isRefreshingPlaylists) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFFFB923C)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Listeler güncelleniyor...",
                            color = Color(0xFFFB923C),
                            fontSize = 11.sp
                        )
                    }
                }

                // 3'lü Filtre Butonları (Tümü, Aktif, Kapalı)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvFilterChip(
                        title = "Tümü",
                        count = totalCount,
                        isSelected = channelFilter == ChannelFilterType.ALL,
                        badgeColor = Color(0xFF64748B),
                        onClick = { viewModel.setChannelFilter(ChannelFilterType.ALL) }
                    )
                    TvFilterChip(
                        title = "Aktif",
                        count = activeCount,
                        isSelected = channelFilter == ChannelFilterType.ACTIVE,
                        badgeColor = Color(0xFF10B981),
                        onClick = { viewModel.setChannelFilter(ChannelFilterType.ACTIVE) }
                    )
                    TvFilterChip(
                        title = "Kapalı",
                        count = offlineCount,
                        isSelected = channelFilter == ChannelFilterType.OFFLINE,
                        badgeColor = Color(0xFFEF4444),
                        onClick = { viewModel.setChannelFilter(ChannelFilterType.OFFLINE) }
                    )
                }
            }

            playlistError?.let { message ->
                Text(text = message, color = Color(0xFFFB923C), fontSize = 12.sp)
            }
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "\"$searchQuery\" ile eşleşen kanal bulunamadı." else "Kanal listesi yükleniyor veya kanal bulunamadı...",
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            } else {
                LaunchedEffect(restoreChannelId, channels.map { it.id }) {
                    val targetId = restoreChannelId
                    if (!restoredChannel && targetId != null && channels.isNotEmpty()) {
                        val targetIndex = channels.indexOfFirst { it.id == targetId }
                        if (targetIndex >= 0) {
                            viewModel.channelGridState.scrollToItem(targetIndex)
                            restoredChannel = true
                        }
                    }
                }

                LazyVerticalGrid(
                    state = viewModel.channelGridState,
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = channels,
                        key = { it.id }
                    ) { channel ->
                        val status = channelStatuses[channel.id] ?: channel.status
                        val isTarget = channel.id == viewModel.lastFocusedChannelId
                        TvChannelCard(
                            channel = channel,
                            status = status,
                            isInitiallyFocused = isTarget,
                            onClick = {
                                viewModel.lastFocusedChannelId = channel.id
                                onChannelClick(channel, 0)
                            },
                            onLongClick = {
                                selectedChannelForSources = channel
                            },
                            onFocused = {
                                viewModel.lastFocusedChannelId = channel.id
                            }
                        )
                    }
                }
            }
        }
    }
}

    // Dialoglar

    if (showGitHubDialog) {
        TvGitHubScannerDialog(
            onDismiss = { showGitHubDialog = false },
            onPlaylistsAdded = { urls ->
                viewModel.refreshPlaylists(urls)
            }
        )
    }

    selectedChannelForSources?.let { channel ->
        TvChannelSourceDialog(
            channel = channel,
            repository = viewModel.repository,
            onDismiss = { selectedChannelForSources = null },
            onSourceSelected = { sourceIndex ->
                onChannelClick(channel, sourceIndex)
            }
        )
    }

    if (showSearchDialog) {
        TvSearchInputDialog(
            initialQuery = searchQuery,
            matchingCount = channels.size,
            onDismiss = { showSearchDialog = false },
            onSearchConfirmed = { query ->
                viewModel.setSearchQuery(query)
                showSearchDialog = false
            },
            onQueryChanged = { query ->
                viewModel.setSearchQuery(query)
            }
        )
    }
}

@Composable
fun TvSourceItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isSelected -> Color(0xFF222634)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> Color(0xFFCBD5E1)
    }
    val borderColor = when {
        isFocused -> Color.White
        isSelected -> Color(0xFFE50914)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .scale(if (isFocused) 1.04f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TvCategorySubItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isSelected -> Color(0xFF1E232F)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> Color.Black
        isSelected -> Color(0xFFE50914)
        else -> Color(0xFF94A3B8)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
            .scale(if (isFocused) 1.04f else 1.0f)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isFocused) Color.White else if (isSelected) Color(0xFF333D52) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TvToolMenuItem(title: String, color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .scale(if (isFocused) 1.04f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else Color(0xFF141A28))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF202A40),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            color = if (isFocused) Color.Black else color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvChannelCard(
    channel: ChannelEntity,
    status: ChannelStatus,
    isInitiallyFocused: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val quality = remember(channel.name) { detectChannelQuality(channel.name) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    LaunchedEffect(isInitiallyFocused) {
        if (isInitiallyFocused) {
            kotlinx.coroutines.delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .focusRequester(focusRequester)
            .scale(if (isFocused) 1.08f else 1.0f)
            .background(
                color = if (isFocused) Color(0xFF282E39) else Color(0xFF1C1F26),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color(0xFFE50914) else Color(0xFF2C303A),
                shape = RoundedCornerShape(12.dp)
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Menu || event.key == Key.DirectionCenter && event.isAltPressed)) {
                    onLongClick()
                    true
                } else false
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Üst Satır: Kategori + Kalite Rozeti + Kaynak Rozeti + Durum Noktası
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol: Kategori + Kalite
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isSport = channel.isSportsChannel()
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSport) Color(0xFFE50914).copy(alpha = 0.25f) else Color(0xFF1976D2).copy(alpha = 0.25f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSport) "SPOR" else "ULUSAL",
                            color = if (isSport) Color(0xFFFF6B6B) else Color(0xFF64B5F6),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Çözünürlük Rozeti (4K, FHD, HD, SD)
                    Box(
                        modifier = Modifier
                            .background(quality.second.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = quality.first,
                            color = quality.second,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sağ: Çoklu Kaynak Sayısı ve Durum Noktası
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.sourceCount > 1) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2C2411), RoundedCornerShape(4.dp))
                                .border(0.8.dp, Color(0xFFFFB300), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "⚡ ${channel.sourceCount}",
                                color = Color(0xFFFFD54F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Aktiflik Durum Noktası
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when (status) {
                                    ChannelStatus.ACTIVE -> Color(0xFF00E676)
                                    ChannelStatus.OFFLINE -> Color(0xFFFF3D00)
                                    ChannelStatus.UNKNOWN -> Color(0xFF6E7681)
                                },
                                shape = RoundedCornerShape(50)
                            )
                            .border(width = 1.dp, color = Color(0x33FFFFFF), shape = RoundedCornerShape(50))
                    )
                }
            }

            // Orta Alan: Kanal Logosu
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (isFocused) Color(0xFFE50914) else Color(0xFF2A313D),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Alt Alan: Kanal İsmi
            Text(
                text = channel.name,
                color = if (isFocused) Color.White else Color(0xFFE0E0E0),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun TvFilterChip(
    title: String,
    count: Int,
    isSelected: Boolean,
    badgeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .scale(if (isFocused) 1.06f else 1.0f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFF2A313D)
                    else -> Color(0xFF191C24)
                }
            )
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> badgeColor
                    else -> Color(0xFF2D323E)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (isFocused) Color.Black else badgeColor,
                        shape = RoundedCornerShape(50)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isFocused) Color.Black else if (isSelected) Color.White else Color(0xFFBBBBBB),
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            )
            if (count >= 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($count)",
                    color = if (isFocused) Color.Black else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TvSearchInputDialog(
    initialQuery: String,
    matchingCount: Int,
    onDismiss: () -> Unit,
    onSearchConfirmed: (String) -> Unit,
    onQueryChanged: (String) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var isInputFocused by remember { mutableStateOf(false) }

    val updateQuery: (String) -> Unit = { newQ ->
        query = newQ
        onQueryChanged(newQ)
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                // 1. Üst Başlık ve Canlı Eşleşme Sayacı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🔍 Kanal Arama",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (query.isNotBlank()) "$matchingCount kanal bulundu" else "Tüm Kanallar",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Metin Giriş Alanı (BasicTextField - TV kumandası veya klavyeyle yazma)
                val inputInteraction = remember { MutableInteractionSource() }
                val isFocusedState by inputInteraction.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(
                            width = if (isFocusedState || isInputFocused) 2.dp else 1.dp,
                            color = if (isFocusedState || isInputFocused) Color.White else Color(0xFF475569),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .focusable(interactionSource = inputInteraction)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Ara",
                            tint = if (isFocusedState || isInputFocused) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Kanal adını yazın (örn: S Sport, beIN, TRT)...",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { updateQuery(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchConfirmed(query) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isInputFocused = it.isFocused }
                            )
                        }
                        if (query.isNotEmpty()) {
                            val clearInteraction = remember { MutableInteractionSource() }
                            val isClearFocused by clearInteraction.collectIsFocusedAsState()
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isClearFocused) Color.White else Color(0xFF334155))
                                    .clickable(interactionSource = clearInteraction, indication = null) {
                                        updateQuery("")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Temizle",
                                    tint = if (isClearFocused) Color.Black else Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Ekran Sanal TV Klavyesi (Kumanda D-pad ile harf harf yazma)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sayılar
                    val numKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        numKeys.forEach { k ->
                            TvKeyboardKey(
                                text = k,
                                modifier = Modifier.weight(1f),
                                onClick = { updateQuery(query + k) }
                            )
                        }
                    }

                    // QWERTY Satır 1
                    val row1Keys = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row1Keys.forEach { k ->
                            TvKeyboardKey(
                                text = k,
                                modifier = Modifier.weight(1f),
                                onClick = { updateQuery(query + k) }
                            )
                        }
                    }

                    // QWERTY Satır 2
                    val row2Keys = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row2Keys.forEach { k ->
                            TvKeyboardKey(
                                text = k,
                                modifier = Modifier.weight(1f),
                                onClick = { updateQuery(query + k) }
                            )
                        }
                    }

                    // QWERTY Satır 3
                    val row3Keys = listOf("Z", "X", "C", "V", "B", "N", "M")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row3Keys.forEach { k ->
                            TvKeyboardKey(
                                text = k,
                                modifier = Modifier.weight(1f),
                                onClick = { updateQuery(query + k) }
                            )
                        }
                    }

                    // Satır 4: Kontroller (Boşluk, Sil, Temizle, Tamam)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TvKeyboardKey(
                            text = "⌴ Boşluk",
                            modifier = Modifier.weight(2f),
                            onClick = { updateQuery("$query ") }
                        )
                        TvKeyboardKey(
                            text = "⌫ Sil",
                            modifier = Modifier.weight(1.3f),
                            onClick = {
                                if (query.isNotEmpty()) {
                                    updateQuery(query.dropLast(1))
                                }
                            }
                        )
                        if (query.isNotEmpty()) {
                            TvKeyboardKey(
                                text = "🗑 Sıfırla",
                                isDanger = true,
                                modifier = Modifier.weight(1.3f),
                                onClick = { updateQuery("") }
                            )
                        }
                        TvKeyboardKey(
                            text = "✓ Tamam",
                            isPrimary = true,
                            modifier = Modifier.weight(2f),
                            onClick = { onSearchConfirmed(query) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Hızlı Arama Önerileri (Tek Tıklamalık Çipler)
                val quickChips = listOf("Spor", "beIN", "S Sport", "TRT", "ATV", "Show", "Kanal D", "Star", "TV8", "Exxen", "Tivibu", "Haber")
                Text(
                    text = "Hızlı Arama Önerileri:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.take(6).forEach { chip ->
                        TvSearchChip(
                            text = chip,
                            modifier = Modifier.weight(1f),
                            onClick = { updateQuery(chip) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.drop(6).forEach { chip ->
                        TvSearchChip(
                            text = chip,
                            modifier = Modifier.weight(1f),
                            onClick = { updateQuery(chip) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Kapatma Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val closeInteraction = remember { MutableInteractionSource() }
                    val isCloseFocused by closeInteraction.collectIsFocusedAsState()
                    Box(
                        modifier = Modifier
                            .scale(if (isCloseFocused) 1.05f else 1.0f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCloseFocused) Color.White else Color(0xFF334155))
                            .focusable(interactionSource = closeInteraction)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Kapat",
                            color = if (isCloseFocused) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvKeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isPrimary -> Color(0xFFE50914)
        isDanger -> Color(0xFF991B1B)
        else -> Color(0xFF1E293B)
    }
    val textColor = when {
        isFocused -> Color.Black
        isPrimary || isDanger -> Color.White
        else -> Color(0xFFE2E8F0)
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .scale(if (isFocused) 1.08f else 1.0f)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF334155),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isFocused || isPrimary || isDanger) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun TvSearchChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.06f else 1.0f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) Color.White else Color(0xFF1E293B))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF334155),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color(0xFF38BDF8),
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
