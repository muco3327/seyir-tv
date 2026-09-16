package tv.newtv.ui.screens
 
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tv.newtv.data.models.Episode
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.Season
import tv.newtv.data.models.SeriesDetail

@Composable
fun TvDetailBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .scale(if (isFocused) 1.06f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else Color(0xFF22252C))
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color(0xFF38BDF8).copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "← Geri Dön",
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TvMovieDetailScreen(
    movieDetail: MovieDetail,
    onPlayClick: (preferDubbing: Boolean?) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val cleanPoster = remember(movieDetail.posterUrl, movieDetail.provider) {
        tv.newtv.utils.PosterUrlUtils.normalize(movieDetail.posterUrl, movieDetail.provider, isMovie = true)
    }

    val referer = remember(cleanPoster, movieDetail.provider) {
        tv.newtv.utils.PosterUrlUtils.getReferer(cleanPoster, movieDetail.provider, isMovie = true)
    }

    val imageRequest = remember(cleanPoster, referer) {
        ImageRequest.Builder(context)
            .data(cleanPoster)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .setHeader("Referer", referer)
            .crossfade(false)
            .build()
    }

    val hasDubbing = movieDetail.dubbingIframes.isNotEmpty() || movieDetail.languages.any { it.contains("dublaj", ignoreCase = true) }
    val hasSubtitle = movieDetail.subtitleIframes.isNotEmpty() || movieDetail.languages.any { it.contains("altyaz", ignoreCase = true) }
    val backFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        runCatching { playFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Backdrop Arka Plan
        AsyncImage(
            model = imageRequest,
            contentDescription = "Backdrop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )
        // Karartma Degradesi
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.95f), Color.Transparent),
                        endX = 1100f
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sol Taraf: Geri Butonu ve Poster (%20 küçültüldü: 260dp -> 208dp)
            Column(
                modifier = Modifier.width(208.dp),
                horizontalAlignment = Alignment.Start
            ) {
                TvDetailBackButton(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .padding(bottom = 16.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = movieDetail.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(48.dp))

            // Sağ Taraf: Detaylar ve Butonlar
            Column(modifier = Modifier.weight(1f)) {
                // Kaynak & Dil Rozetleri
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (movieDetail.provider.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2563EB), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = movieDetail.provider.uppercase(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (hasDubbing) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE50914), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TR DUBLAJ",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (hasSubtitle) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0284C7), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TR ALTYAZI",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = movieDetail.title,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = movieDetail.description.ifEmpty { "Film açıklaması bulunmuyor." },
                    color = Color(0xFFE2E8F0),
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))

                val favoriteInteraction = remember { MutableInteractionSource() }
                val isFavoriteFocused by favoriteInteraction.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .scale(if (isFavoriteFocused) 1.05f else 1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isFavoriteFocused) Color.White else Color(0xFF30343B))
                        .border(
                            width = if (isFavoriteFocused) 2.dp else 1.dp,
                            color = if (isFavoriteFocused) Color.White else Color(0xFF4A5568),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .focusable(interactionSource = favoriteInteraction)
                        .clickable(interactionSource = favoriteInteraction, indication = null, onClick = onToggleFavorite)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (isFavorite) "★ Favorilerden Çıkar" else "☆ Favorilere Ekle",
                        color = if (isFavoriteFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Oynatma Butonları: Filmde hangi dil seçeneği varsa otomatik o butonlar görünür
                val canPlayDub = movieDetail.dubbingIframes.isNotEmpty() || hasDubbing
                val canPlaySub = movieDetail.subtitleIframes.isNotEmpty() || hasSubtitle

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (canPlayDub && canPlaySub) {
                        // 1. Türkçe Dublaj Butonu
                        val dubInteraction = remember { MutableInteractionSource() }
                        val isDubFocused by dubInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .scale(if (isDubFocused) 1.06f else 1.0f)
                                .focusRequester(playFocusRequester)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDubFocused) Color.White else Color(0x33FFFFFF))
                                .border(
                                    width = 2.dp,
                                    color = if (isDubFocused) Color.White else Color(0x4DFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = dubInteraction)
                                .clickable(
                                    interactionSource = dubInteraction,
                                    indication = null,
                                    onClick = { onPlayClick(true) }
                                )
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "🎬 Dublaj",
                                color = if (isDubFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Türkçe Altyazı Butonu
                        val subInteraction = remember { MutableInteractionSource() }
                        val isSubFocused by subInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .scale(if (isSubFocused) 1.06f else 1.0f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSubFocused) Color.White else Color(0x33FFFFFF))
                                .border(
                                    width = 2.dp,
                                    color = if (isSubFocused) Color.White else Color(0x4DFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = subInteraction)
                                .clickable(
                                    interactionSource = subInteraction,
                                    indication = null,
                                    onClick = { onPlayClick(false) }
                                )
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "📝 Altyazı",
                                color = if (isSubFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (canPlayDub) {
                        // Sadece Dublaj Butonu
                        val dubInteraction = remember { MutableInteractionSource() }
                        val isDubFocused by dubInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .scale(if (isDubFocused) 1.08f else 1.0f)
                                .focusRequester(playFocusRequester)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDubFocused) Color.White else Color(0x33FFFFFF))
                                .border(
                                    width = 2.dp,
                                    color = if (isDubFocused) Color.White else Color(0x4DFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = dubInteraction)
                                .clickable(
                                    interactionSource = dubInteraction,
                                    indication = null,
                                    onClick = { onPlayClick(true) }
                                )
                                .padding(horizontal = 28.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "🎬 Oynat (Dublaj)",
                                color = if (isDubFocused) Color.Black else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (canPlaySub) {
                        // Sadece Altyazı Butonu
                        val subInteraction = remember { MutableInteractionSource() }
                        val isSubFocused by subInteraction.collectIsFocusedAsState()
                        Box(
                            modifier = Modifier
                                .scale(if (isSubFocused) 1.08f else 1.0f)
                                .focusRequester(playFocusRequester)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSubFocused) Color.White else Color(0x33FFFFFF))
                                .border(
                                    width = 2.dp,
                                    color = if (isSubFocused) Color.White else Color(0x4DFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = subInteraction)
                                .clickable(
                                    interactionSource = subInteraction,
                                    indication = null,
                                    onClick = { onPlayClick(false) }
                                )
                                .padding(horizontal = 28.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "📝 Oynat (Altyazı)",
                                color = if (isSubFocused) Color.Black else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Varsayılan Tek Başlat Butonu
                        val playInteractionSource = remember { MutableInteractionSource() }
                        val isPlayFocused by playInteractionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .scale(if (isPlayFocused) 1.08f else 1.0f)
                                .focusRequester(playFocusRequester)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPlayFocused) Color.White else Color(0x33FFFFFF))
                                .border(
                                    width = 2.dp,
                                    color = if (isPlayFocused) Color.White else Color(0x4DFFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable(interactionSource = playInteractionSource)
                                .clickable(
                                    interactionSource = playInteractionSource,
                                    indication = null,
                                    onClick = { onPlayClick(null) }
                                )
                                .padding(horizontal = 32.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = "▶ Hemen İzle",
                                color = if (isPlayFocused) Color.Black else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvDetailScreen(
    seriesDetail: SeriesDetail,
    onEpisodeClick: (Episode, Boolean) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val cleanPoster = remember(seriesDetail.posterUrl, seriesDetail.provider) {
        tv.newtv.utils.PosterUrlUtils.normalize(seriesDetail.posterUrl, seriesDetail.provider, isMovie = false)
    }

    val referer = remember(cleanPoster, seriesDetail.provider) {
        tv.newtv.utils.PosterUrlUtils.getReferer(cleanPoster, seriesDetail.provider, isMovie = false)
    }

    val imageRequest = remember(cleanPoster, referer) {
        ImageRequest.Builder(context)
            .data(cleanPoster)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .setHeader("Referer", referer)
            .crossfade(false)
            .build()
    }

    val backFocusRequester = remember { FocusRequester() }
    val seasonFocusRequester = remember { FocusRequester() }
    val leftScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        runCatching { seasonFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Arka Plan Karartmalı Afiş
        AsyncImage(
            model = imageRequest,
            contentDescription = "Backdrop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.2f
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Sol Alan: Dizi Bilgisi ve Afiş (%20 küçültüldü: 220dp -> 176dp)
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .verticalScroll(leftScrollState)
                    .padding(end = 24.dp)
            ) {
                TvDetailBackButton(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .padding(bottom = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .width(176.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = seriesDetail.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Rozetler
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (seriesDetail.provider.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF7C3AED), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = seriesDetail.provider.uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE50914), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(text = "TR DUBLAJ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0284C7), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(text = "TR ALTYAZI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = seriesDetail.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val favoriteInteraction = remember { MutableInteractionSource() }
                val isFavoriteFocused by favoriteInteraction.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .scale(if (isFavoriteFocused) 1.05f else 1.0f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFavoriteFocused) Color.White else Color(0xFF30343B))
                        .border(
                            width = if (isFavoriteFocused) 2.dp else 1.dp,
                            color = if (isFavoriteFocused) Color.White else Color(0xFF4A5568),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusable(interactionSource = favoriteInteraction)
                        .clickable(interactionSource = favoriteInteraction, indication = null, onClick = onToggleFavorite)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isFavorite) "★ Favorilerden Çıkar" else "☆ Favorilere Ekle",
                        color = if (isFavoriteFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = seriesDetail.description.ifEmpty { "Dizi açıklaması bulunmuyor." },
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Sağ Alan: Sezonlar ve Bölümler
            Column(
                modifier = Modifier
                    .weight(1.9f)
                    .fillMaxHeight()
            ) {
                // Sezonlar Yatay Sekmesi (Horizontal Row)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(seriesDetail.seasons.size) { index ->
                        TvSeasonButton(
                            season = seriesDetail.seasons[index],
                            isSelected = selectedSeasonIndex == index,
                            modifier = if (index == 0) Modifier.focusRequester(seasonFocusRequester) else Modifier,
                            onClick = { selectedSeasonIndex = index }
                        )
                    }
                }

                // Seçilen Sezonun Bölümleri (Dikey Liste)
                val currentSeason = seriesDetail.seasons.getOrNull(selectedSeasonIndex)
                if (currentSeason != null) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(currentSeason.episodes) { episode ->
                            TvEpisodeButton(
                                episode = episode,
                                onPlayClick = { preferDubbing ->
                                    onEpisodeClick(episode, preferDubbing)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvSeasonButton(season: Season, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.1f else 1.0f)
            .background(
                color = if (isFocused) Color.White else if (isSelected) Color(0xFFE50914) else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = season.name,
            color = if (isFocused) Color.Black else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TvEpisodeButton(
    episode: Episode,
    onPlayClick: (preferDubbing: Boolean) -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    val isRowFocused by rowInteraction.collectIsFocusedAsState()

    val dubInteraction = remember { MutableInteractionSource() }
    val isDubFocused by dubInteraction.collectIsFocusedAsState()

    val subInteraction = remember { MutableInteractionSource() }
    val isSubFocused by subInteraction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isRowFocused || isDubFocused || isSubFocused) 1.02f else 1.0f)
            .background(
                if (isRowFocused) Color(0xFF333842) else Color(0xFF1E2129),
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (isRowFocused) 2.dp else 1.dp,
                color = if (isRowFocused) Color.White else Color(0xFF2E3440),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Bölüm ${episode.episodeNumber}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Türkçe Dublaj Butonu
                Box(
                    modifier = Modifier
                        .scale(if (isDubFocused) 1.10f else 1.0f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isDubFocused) Color.White else Color(0xFFE50914))
                        .border(
                            width = if (isDubFocused) 2.dp else 0.dp,
                            color = if (isDubFocused) Color(0xFFFFCC00) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .focusable(interactionSource = dubInteraction)
                        .clickable(
                            interactionSource = dubInteraction,
                            indication = null,
                            onClick = { onPlayClick(true) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "▶ Dublaj",
                        color = if (isDubFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Türkçe Altyazı Butonu
                Box(
                    modifier = Modifier
                        .scale(if (isSubFocused) 1.10f else 1.0f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSubFocused) Color.White else Color(0xFF0284C7))
                        .border(
                            width = if (isSubFocused) 2.dp else 0.dp,
                            color = if (isSubFocused) Color(0xFFFFCC00) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .focusable(interactionSource = subInteraction)
                        .clickable(
                            interactionSource = subInteraction,
                            indication = null,
                            onClick = { onPlayClick(false) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "💬 Altyazı",
                        color = if (isSubFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
