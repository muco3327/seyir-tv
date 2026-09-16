package tv.newtv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.SeriesItem
import tv.newtv.data.models.VodCategory
import tv.newtv.ui.viewmodel.TvVodViewModel

data class VodRowItem(
    val title: String,
    val posterUrl: String,
    val provider: String = "",
    val languages: List<String> = emptyList(),
    val isInitiallyFocused: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun TvVodScreen(
    title: String,
    movies: Boolean,
    viewModel: TvVodViewModel,
    onMovieClick: (MovieItem) -> Unit,
    onSeriesClick: (SeriesItem) -> Unit
) {
    val movieCategories by viewModel.movies.collectAsStateWithLifecycle()
    val seriesCategories by viewModel.series.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val movieSearchResults by viewModel.movieSearchResults.collectAsStateWithLifecycle()
    val seriesSearchResults by viewModel.seriesSearchResults.collectAsStateWithLifecycle()
    val searchProviderNotice by viewModel.searchProviderNotice.collectAsStateWithLifecycle()
    val movieCatalogPage by viewModel.movieCatalogPage.collectAsStateWithLifecycle()
    val seriesCatalogPage by viewModel.seriesCatalogPage.collectAsStateWithLifecycle()
    val movieCatalogPageCount by viewModel.movieCatalogPageCount.collectAsStateWithLifecycle()
    val seriesCatalogPageCount by viewModel.seriesCatalogPageCount.collectAsStateWithLifecycle()
    val movieCatalogScanStatus by viewModel.movieCatalogScanStatus.collectAsStateWithLifecycle()
    val seriesCatalogScanStatus by viewModel.seriesCatalogScanStatus.collectAsStateWithLifecycle()
    val catalogScanStatus = if (movies) movieCatalogScanStatus else seriesCatalogScanStatus

    val selectedCategory = viewModel.selectedCategory

    LaunchedEffect(movies) { 
        viewModel.clearSearch()
        if (movies) viewModel.loadMovies() else viewModel.loadSeries() 
    }

    // Arama yazıldıkça gecikmeli (debounce) otomatik arama tetikleme
    LaunchedEffect(searchQuery, movies) {
        if (searchQuery.isNotBlank()) {
            delay(500)
            viewModel.search(searchQuery, movies)
        }
    }
    
    val categoriesToDisplay = if (movies) movieCategories else seriesCategories
    // Seçili bir kategori varsa, o kategorinin TAMAMINI açan Grid ekranını göster
    if (selectedCategory != null) {
        TvCategoryDetailView(
            category = selectedCategory,
            isMovie = movies,
            viewModel = viewModel,
            onBack = { viewModel.selectedCategory = null },
            onMovieClick = onMovieClick,
            onSeriesClick = onSeriesClick
        )
        return
    }

    val scrollState = if (movies) viewModel.movieScrollState else viewModel.seriesScrollState

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // Üst Başlık & TV Arama Çubuğu
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (movies) Color(0xFFE50914) else Color(0xFFFF9800),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (movies) "FİLM MERKEZİ" else "DİZİ MERKEZİ",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Android TV Uyumlu Arama Çubuğu
                TvSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onSearch = { viewModel.search(searchQuery, movies) },
                    onClear = { viewModel.clearSearch() },
                    placeholder = if (movies) "Film adı ara..." else "Dizi adı ara..."
                )
            }
        }

        // ARAMA MODU AKTİFSE
        if (searchQuery.isNotBlank()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "🔍 \"$searchQuery\" için Arama Sonuçları",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sonuçlar aranıyor, lütfen bekleyin...", color = Color.LightGray, fontSize = 16.sp)
                    }
                }
            } else {
                val resultsCount = if (movies) movieSearchResults.size else seriesSearchResults.size
                if (searchProviderNotice != null) {
                    item {
                        Text(
                            text = searchProviderNotice!!,
                            color = Color(0xFFFFB74D),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
                if (resultsCount == 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aramanıza uygun içerik bulunamadı.", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    val searchItems = if (movies) {
                        movieSearchResults.map { item ->
                            val isTarget = item.url == viewModel.lastFocusedMovieUrl
                            VodRowItem(
                                title = item.title,
                                posterUrl = item.posterUrl,
                                provider = item.provider,
                                languages = item.languages,
                                isInitiallyFocused = isTarget,
                                onClick = {
                                    viewModel.lastFocusedMovieUrl = item.url
                                    onMovieClick(item)
                                }
                            )
                        }
                    } else {
                        seriesSearchResults.map { item ->
                            val isTarget = item.url == viewModel.lastFocusedSeriesUrl
                            VodRowItem(
                                title = item.title,
                                posterUrl = item.posterUrl,
                                provider = item.provider,
                                languages = item.languages,
                                isInitiallyFocused = isTarget,
                                onClick = {
                                    viewModel.lastFocusedSeriesUrl = item.url
                                    onSeriesClick(item)
                                }
                            )
                        }
                    }

                    val chunked = searchItems.chunked(12)
                    chunked.forEachIndexed { idx, rowItems ->
                        val rowTitle = if (chunked.size == 1) "Bulunan İçerikler ($resultsCount)" else "Bulunan İçerikler (${idx + 1}/${chunked.size})"
                        item {
                            TvVodRow(
                                categoryTitle = rowTitle,
                                isMovie = movies,
                                items = rowItems
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (movies) Color(0xFFE50914) else Color(0xFFFF9800), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text("TÜM KAYNAKLAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        catalogScanStatus ?: "Yerel katalog hazırlanıyor…",
                        color = if (isLoading) Color(0xFFFFC107) else Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }

            // Hızlı Kategori Seçim Çubuğu (Chips)
            if (categoriesToDisplay.isNotEmpty()) {
                item {
                    TvCategoryChipsRow(
                        categories = categoriesToDisplay,
                        isMovie = movies,
                        onCategoryClick = { viewModel.selectedCategory = it }
                    )
                }
            }

            when {
                isLoading && categoriesToDisplay.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("İçerikler yükleniyor, lütfen bekleyin…", color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }
                error != null -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(error!!, color = Color(0xFFFF5252), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            val retryInteraction = remember { MutableInteractionSource() }
                            val isRetryFocused by retryInteraction.collectIsFocusedAsState()
                            Box(
                                modifier = Modifier
                                    .scale(if (isRetryFocused) 1.08f else 1.0f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRetryFocused) Color.White else Color(0xFFE50914))
                                    .border(
                                        width = 2.dp,
                                        color = if (isRetryFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(
                                        interactionSource = retryInteraction,
                                        indication = null
                                    ) {
                                        if (movies) viewModel.loadMovies() else viewModel.loadSeries()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Yeniden Dene",
                                    color = if (isRetryFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                categoriesToDisplay.isEmpty() -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Gösterilecek içerik bulunamadı.", color = Color.LightGray, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            val refreshInteraction = remember { MutableInteractionSource() }
                            val isRefreshFocused by refreshInteraction.collectIsFocusedAsState()
                            Box(
                                modifier = Modifier
                                    .scale(if (isRefreshFocused) 1.08f else 1.0f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRefreshFocused) Color.White else Color(0xFF2E2E2E))
                                    .border(
                                        width = 2.dp,
                                        color = if (isRefreshFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(
                                        interactionSource = refreshInteraction,
                                        indication = null
                                    ) {
                                        if (movies) viewModel.loadMovies() else viewModel.loadSeries()
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Listeyi Yenile",
                                    color = if (isRefreshFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    val pageItems = categoriesToDisplay.flatMap { it.items }
                    pageItems.chunked(6).forEach { gridRow ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                gridRow.forEach { item ->
                            val itemTitle = if (movies) (item as MovieItem).title else (item as SeriesItem).title
                            val poster = if (movies) (item as MovieItem).posterUrl else (item as SeriesItem).posterUrl
                            val itemUrl = if (movies) (item as MovieItem).url else (item as SeriesItem).url
                            val itemProvider = if (movies) (item as MovieItem).provider else (item as SeriesItem).provider
                            val itemLanguages = if (movies) (item as MovieItem).languages else (item as SeriesItem).languages
                            val isTarget = if (movies) itemUrl == viewModel.lastFocusedMovieUrl else itemUrl == viewModel.lastFocusedSeriesUrl
                            val action = if (movies) {
                                {
                                    viewModel.lastFocusedMovieUrl = itemUrl
                                    onMovieClick(item as MovieItem)
                                }
                            } else {
                                {
                                    viewModel.lastFocusedSeriesUrl = itemUrl
                                    onSeriesClick(item as SeriesItem)
                                }
                            }
                                    TvVodPosterCard(
                                title = itemTitle,
                                posterUrl = poster,
                                        isMovie = movies,
                                provider = itemProvider,
                                languages = itemLanguages,
                                isInitiallyFocused = isTarget,
                                onClick = action
                            )
                        }
                                repeat(6 - gridRow.size) { Spacer(Modifier.width(135.dp)) }
                            }
                        }
                    }

                    // 50 öğelik numaralı katalog sayfaları
                    item {
                        TvCatalogPagination(
                            currentPage = if (movies) movieCatalogPage else seriesCatalogPage,
                            pageCount = if (movies) movieCatalogPageCount else seriesCatalogPageCount,
                            isMovie = movies,
                            onPageSelected = { page ->
                                if (movies) viewModel.setMovieCatalogPage(page) else viewModel.setSeriesCatalogPage(page)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCatalogPagination(
    currentPage: Int,
    pageCount: Int,
    isMovie: Boolean,
    onPageSelected: (Int) -> Unit
) {
    val visiblePages = remember(currentPage, pageCount) {
        val start = (currentPage - 4).coerceAtLeast(1)
        val end = (start + 8).coerceAtMost(pageCount)
        (maxOf(1, end - 8)..end).toList()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sayfa $currentPage / $pageCount • Her sayfada en fazla 50 içerik", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentPage > 1) item { TvPageButton("‹", false, isMovie) { onPageSelected(currentPage - 1) } }
            items(visiblePages.size) { index ->
                val page = visiblePages[index]
                TvPageButton(page.toString(), page == currentPage, isMovie) { onPageSelected(page) }
            }
            if (currentPage < pageCount) item { TvPageButton("›", false, isMovie) { onPageSelected(currentPage + 1) } }
        }
    }
}

@Composable
private fun TvPageButton(label: String, selected: Boolean, isMovie: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> Color.White
                    selected -> if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800)
                    else -> Color(0xFF252932)
                }
            )
            .border(1.dp, if (focused) Color.White else Color(0xFF3A3F49), RoundedCornerShape(8.dp))
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    placeholder: String = "Ara...",
    modifier: Modifier = Modifier
) {
    var isInputFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width(360.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (isInputFocused) Color(0xFF2E3238) else Color(0xFF1C1E22))
            .border(
                width = if (isInputFocused) 2.dp else 1.dp,
                color = if (isInputFocused) Color(0xFFE50914) else Color(0xFF383C44),
                shape = RoundedCornerShape(21.dp)
            )
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Ara",
            tint = if (isInputFocused) Color.White else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF888888),
                    fontSize = 13.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch() }
                ),
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
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (isClearFocused) Color.White else Color.Transparent)
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = null
                    ) { onClear() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Temizle",
                    tint = if (isClearFocused) Color.Black else Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TvHeroBanner(
    title: String,
    posterUrl: String,
    isMovie: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current

    val cleanUrl = remember(posterUrl) {
        var p = posterUrl.trim()
        when {
            p.startsWith("//") -> p = "https:$p"
            p.startsWith("/") -> p = if (isMovie) "https://selcukflix.com$p" else "https://dizilla.now$p"
        }
        p.replace("https://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
            .replace("http://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
    }

    val imageRequest = remember(cleanUrl) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .setHeader("Referer", if (isMovie) "https://selcukflix.com/" else "https://dizilla.now/")
            .crossfade(false)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .scale(if (isFocused) 1.02f else 1.0f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(16.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = onClick)
    ) {
        // Arka Plan Görseli
        AsyncImage(
            model = imageRequest,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Karartma Gradienti
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 60f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "GÜNÜN ÖNE ÇIKANI",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "▶ Hemen İzle",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TvVodRow(
    categoryTitle: String,
    isMovie: Boolean,
    items: List<VodRowItem>,
    onCategoryClick: (() -> Unit)? = null
) {
    val headerInteraction = remember { MutableInteractionSource() }
    val isHeaderFocused by headerInteraction.collectIsFocusedAsState()

    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .scale(if (isHeaderFocused) 1.02f else 1.0f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isHeaderFocused) Color(0xFF282C35) else Color.Transparent)
                .border(
                    width = if (isHeaderFocused) 2.dp else 0.dp,
                    color = if (isHeaderFocused) (if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800)) else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .focusable(interactionSource = headerInteraction)
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                    onClick = { onCategoryClick?.invoke() }
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .background(
                            if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800),
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = categoryTitle,
                    color = if (isHeaderFocused) Color.White else Color(0xFFEEEEEE),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sağ Taraf: "Tümünü Aç / Gör" Butonu
            if (onCategoryClick != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isHeaderFocused) (if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800)) else Color(0xFF20232A)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Tümünü Aç (${items.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Tümünü Gör",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items.size) { index ->
                val itm = items[index]
                TvVodPosterCard(
                    title = itm.title,
                    posterUrl = itm.posterUrl,
                    isMovie = isMovie,
                    provider = itm.provider,
                    languages = itm.languages,
                    isInitiallyFocused = itm.isInitiallyFocused,
                    modifier = Modifier.width(135.dp),
                    onClick = itm.onClick
                )
            }
        }
    }
}

@Composable
fun TvVodPosterCard(
    title: String,
    posterUrl: String,
    isMovie: Boolean,
    provider: String = "",
    languages: List<String> = emptyList(),
    isInitiallyFocused: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(isInitiallyFocused) {
        if (isInitiallyFocused) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val cleanUrl = remember(posterUrl, provider) {
        var p = posterUrl.trim()
        val provLower = provider.lowercase()
        when {
            p.startsWith("//") -> p = "https:$p"
            p.startsWith("/") -> {
                val base = when {
                    provLower.contains("filmmodu") -> "https://filmmodu.cc"
                    provLower.contains("jetfilm") -> "https://jetfilmizle.top"
                    provLower.contains("sezonluk") -> "https://sezonlukdizi.cc"
                    provLower.contains("dizilla") -> "https://dizilla.now"
                    isMovie -> "https://selcukflix.com"
                    else -> "https://dizilla.now"
                }
                p = "$base$p"
            }
        }
        p.replace("https://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
            .replace("http://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
    }

    val referer = remember(provider, isMovie) {
        val provLower = provider.lowercase()
        when {
            provLower.contains("filmmodu") -> "https://filmmodu.cc/"
            provLower.contains("jetfilm") -> "https://jetfilmizle.top/"
            provLower.contains("sezonluk") -> "https://sezonlukdizi.cc/"
            provLower.contains("dizilla") -> "https://dizilla.now/"
            isMovie -> "https://selcukflix.com/"
            else -> "https://dizilla.now/"
        }
    }

    val imageRequest = remember(cleanUrl, referer) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .setHeader("Referer", referer)
            .crossfade(false)
            .build()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.675f)
                .focusRequester(focusRequester)
                .scale(if (isFocused) 1.08f else 1.0f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF22262E), Color(0xFF14161A))
                    ),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) Color.White else Color(0xFF2A2D34),
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .focusable(interactionSource = interactionSource)
                .clickable(onClick = onClick)
        ) {
            // Arka planda zarif fallback ikon/başlık
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isMovie) Icons.Default.Movie else Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = title,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Kapak Afişi
            if (posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Üst Bilgi Rozetleri (Kaynak Sağlayıcı & Dil)
            if (provider.isNotBlank() || languages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (provider.isNotBlank()) {
                        val provColor = when (provider.lowercase()) {
                            "hdfilmcehennemi", "selcukflix" -> Color(0xFFE50914)
                            "filmmodu" -> Color(0xFF00ADB5)
                            "jetfilmizle" -> Color(0xFFFF5722)
                            "dizilla" -> Color(0xFFFF9800)
                            "sezonlukdizi" -> Color(0xFF7C4DFF)
                            else -> Color(0xFF455A64)
                        }
                        val provLabel = when (provider.lowercase()) {
                            "hdfilmcehennemi" -> "HDFILM"
                            "filmmodu" -> "MODU"
                            "jetfilmizle" -> "JET"
                            "dizilla" -> "DIZILLA"
                            "sezonlukdizi" -> "SEZONLUK"
                            else -> provider.uppercase().take(8)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(provColor.copy(alpha = 0.92f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = provLabel,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (languages.isNotEmpty()) {
                        val hasDub = languages.any { it.contains("Dublaj", ignoreCase = true) }
                        val hasSub = languages.any { it.contains("Altyazı", ignoreCase = true) || it.contains("Altyazi", ignoreCase = true) }
                        val langLabel = when {
                            hasDub && hasSub -> "D&A"
                            hasDub -> "DUB"
                            hasSub -> "ALT"
                            else -> languages.first().take(4).uppercase()
                        }
                        val langBg = if (hasDub && hasSub) Color(0xFF2E7D32) else if (hasDub) Color(0xFF1565C0) else Color(0xFF6D4C41)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(langBg.copy(alpha = 0.92f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = langLabel,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = if (isFocused) Color.White else Color(0xFFD0D0D0),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun TvCategoryChipsRow(
    categories: List<VodCategory<*>>,
    isMovie: Boolean,
    onCategoryClick: (VodCategory<*>) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        items(categories.size) { index ->
            val cat = categories[index]
            val interaction = remember { MutableInteractionSource() }
            val isFocused by interaction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .scale(if (isFocused) 1.06f else 1.0f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isFocused) Color.White else Color(0xFF20232A)
                    )
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) (if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800)) else Color(0xFF323642),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .focusable(interactionSource = interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onCategoryClick(cat) }
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cat.title,
                        color = if (isFocused) Color.Black else Color(0xFFDDDDDD),
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${cat.items.size})",
                        color = if (isFocused) Color(0xFFE50914) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TvCategoryDetailView(
    category: VodCategory<*>,
    isMovie: Boolean,
    viewModel: TvVodViewModel,
    onBack: () -> Unit,
    onMovieClick: (MovieItem) -> Unit,
    onSeriesClick: (SeriesItem) -> Unit
) {
    BackHandler { onBack() }

    val backInteractionSource = remember { MutableInteractionSource() }
    val isBackFocused by backInteractionSource.collectIsFocusedAsState()

    LaunchedEffect(category) {
        runCatching { viewModel.categoryDetailGridState.scrollToItem(0) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101216))
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)
    ) {
        // Üst Başlık & Kontrol Çubuğu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // TV Kumandası Uyumlu Geri Butonu
                Box(
                    modifier = Modifier
                        .scale(if (isBackFocused) 1.08f else 1.0f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBackFocused) Color.White else Color(0xFF22252C))
                        .border(
                            width = 2.dp,
                            color = if (isBackFocused) (if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800)) else Color(0xFF333842),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusable(interactionSource = backInteractionSource)
                        .clickable(
                            interactionSource = backInteractionSource,
                            indication = null,
                            onClick = onBack
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri Dön",
                            tint = if (isBackFocused) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Geri Dön",
                            color = if (isBackFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isMovie) Color(0xFFE50914) else Color(0xFFFF9800),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isMovie) "FİLM KATEGORİSİ" else "DİZİ KATEGORİSİ",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = category.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Toplam İçerik Rozeti
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF22252C))
                    .border(1.dp, Color(0xFF333842), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Toplam ${category.items.size} İçerik",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Tam Kategori Izgarası (Tüm İçerikler - Hafızalı Grid Durumu)
        LazyVerticalGrid(
            state = viewModel.categoryDetailGridState,
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(category.items.size) { index ->
                val rawItem = category.items[index]
                val itemTitle = if (isMovie) (rawItem as MovieItem).title else (rawItem as SeriesItem).title
                val posterUrl = if (isMovie) (rawItem as MovieItem).posterUrl else (rawItem as SeriesItem).posterUrl
                val itemUrl = if (isMovie) (rawItem as MovieItem).url else (rawItem as SeriesItem).url
                val itemProvider = if (isMovie) (rawItem as MovieItem).provider else (rawItem as SeriesItem).provider
                val itemLanguages = if (isMovie) (rawItem as MovieItem).languages else (rawItem as SeriesItem).languages
                val isTarget = if (isMovie) itemUrl == viewModel.lastFocusedMovieUrl else itemUrl == viewModel.lastFocusedSeriesUrl
                val clickAction = if (isMovie) {
                    {
                        viewModel.lastFocusedMovieUrl = itemUrl
                        onMovieClick(rawItem as MovieItem)
                    }
                } else {
                    {
                        viewModel.lastFocusedSeriesUrl = itemUrl
                        onSeriesClick(rawItem as SeriesItem)
                    }
                }

                TvVodPosterCard(
                    title = itemTitle,
                    posterUrl = posterUrl,
                    isMovie = isMovie,
                    provider = itemProvider,
                    languages = itemLanguages,
                    isInitiallyFocused = isTarget,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = clickAction
                )
            }
        }
    }
}
