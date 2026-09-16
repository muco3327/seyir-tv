package tv.newtv.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tv.newtv.ui.screens.TvHomeScreen
import tv.newtv.ui.screens.TvIptvScreen
import tv.newtv.ui.screens.TvVodScreen
import tv.newtv.ui.screens.TvPlayerScreen
import tv.newtv.ui.screens.TvDetailScreen
import tv.newtv.ui.screens.TvMovieDetailScreen
import tv.newtv.ui.screens.TvWatchHistoryScreen
import tv.newtv.ui.screens.TvGitHubScannerDialog
import tv.newtv.ui.viewmodel.TvMainViewModel
import tv.newtv.ui.viewmodel.TvPlayerViewModel
import tv.newtv.ui.viewmodel.TvVodViewModel

enum class NavRoute { HOME, IPTV, HISTORY, MOVIES, SERIES, DETAIL, MOVIE_DETAIL, PLAYER }

@Composable
fun TvAppNavigation() {
    var currentRoute by remember { mutableStateOf(NavRoute.HOME) }
    var playerReturnRoute by remember { mutableStateOf(NavRoute.IPTV) }
    var detailReturnRoute by remember { mutableStateOf(NavRoute.MOVIES) }

    var showGitHubDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    
    val mainViewModel: TvMainViewModel = viewModel()
    val isStarting by mainViewModel.isStarting.collectAsStateWithLifecycle()
    val startupMessage by mainViewModel.startupMessage.collectAsStateWithLifecycle()
    val startupProgress by mainViewModel.startupProgress.collectAsStateWithLifecycle()
    if (isStarting) {
        tv.newtv.ui.screens.TvStartupScreen(startupMessage, startupProgress)
        return
    }
    val playerViewModel: TvPlayerViewModel = viewModel()
    val vodViewModel: TvVodViewModel = viewModel()
    val playableRequest by vodViewModel.playableRequest.collectAsStateWithLifecycle()
    val movieDetail by vodViewModel.movieDetail.collectAsStateWithLifecycle()
    val seriesDetail by vodViewModel.seriesDetail.collectAsStateWithLifecycle()
    val vodBusy by vodViewModel.isLoading.collectAsStateWithLifecycle()
    val vodError by vodViewModel.error.collectAsStateWithLifecycle()
    val favoriteUrls by vodViewModel.favoriteUrls.collectAsStateWithLifecycle()
    val favorites by vodViewModel.favorites.collectAsStateWithLifecycle()

    LaunchedEffect(playableRequest) {
        playableRequest?.let { req ->
            vodViewModel.consumePlayableRequest()
            if (req.iframes.isNotEmpty()) {
                playerViewModel.loadStreamFromIframe(
                    iframeUrls = req.iframes,
                    title = req.title,
                    isMovie = req.isMovie,
                    posterUrl = req.posterUrl,
                    dubbingIframes = req.dubbingIframes,
                    subtitleIframes = req.subtitleIframes,
                    selectedLanguage = req.selectedLanguage,
                    resolvedSource = req.resolvedSource
                )
                playerReturnRoute = if (req.isMovie) NavRoute.MOVIE_DETAIL else NavRoute.DETAIL
                currentRoute = NavRoute.PLAYER
            }
        }
    }

    // Otomatik Detay Yönlendirici (Film sekmesinden Dizi açılırsa NavRoute.DETAIL'e, Film açılırsa NavRoute.MOVIE_DETAIL'e geç)
    LaunchedEffect(movieDetail) {
        if (movieDetail != null && currentRoute == NavRoute.MOVIES) {
            currentRoute = NavRoute.MOVIE_DETAIL
        }
    }

    LaunchedEffect(seriesDetail) {
        if (seriesDetail != null && (currentRoute == NavRoute.MOVIES || currentRoute == NavRoute.SERIES)) {
            currentRoute = NavRoute.DETAIL
        }
    }

    // VOD ekranlarındayken hata varsa geri tuşu hatayı temizlesin, uygulamayı kapatmasın
    androidx.activity.compose.BackHandler(enabled = (currentRoute == NavRoute.MOVIES || currentRoute == NavRoute.SERIES) && vodError != null) {
        vodViewModel.clearError()
    }

    // Film detay sayfasındayken geri tuşu filmler listesine döndürsün
    androidx.activity.compose.BackHandler(enabled = currentRoute == NavRoute.MOVIE_DETAIL) {
        vodViewModel.clearMovieDetail()
        currentRoute = NavRoute.MOVIES
    }

    // Dizi detay sayfasındayken geri tuşu geldiği listeye (MOVIES veya SERIES) döndürsün
    androidx.activity.compose.BackHandler(enabled = currentRoute == NavRoute.DETAIL) {
        vodViewModel.clearSeriesDetail()
        currentRoute = detailReturnRoute
    }

    // Alt ekranlardayken (Canlı TV, Geçmiş, Filmler, Diziler) Geri tuşuna basıldığında Ana Ekrana dön
    androidx.activity.compose.BackHandler(enabled = currentRoute == NavRoute.IPTV) {
        currentRoute = NavRoute.HOME
    }
    androidx.activity.compose.BackHandler(enabled = currentRoute == NavRoute.HISTORY) {
        currentRoute = NavRoute.HOME
    }
    androidx.activity.compose.BackHandler(enabled = (currentRoute == NavRoute.MOVIES || currentRoute == NavRoute.SERIES) && vodError == null) {
        currentRoute = NavRoute.HOME
    }

    // ================= ANA EKRAN (DASHBOARD) =================
    if (currentRoute == NavRoute.HOME) {
        TvHomeScreen(
            onOpenIptv = {
                currentRoute = NavRoute.IPTV
            },
            onOpenMovies = {
                vodViewModel.loadMovies(force = false)
                currentRoute = NavRoute.MOVIES
            },
            onOpenSeries = {
                vodViewModel.loadSeries(force = false)
                currentRoute = NavRoute.SERIES
            },
            onOpenHistory = {
                currentRoute = NavRoute.HISTORY
            },
            onOpenSettings = {
                // Ayarlar menüsü optimum performans için kaldırıldı.
            },
            onOpenGitHubScanner = {
                showGitHubDialog = true
            },
            onExit = {
                activity?.finish()
            }
        )



        if (showGitHubDialog) {
            TvGitHubScannerDialog(
                onDismiss = { showGitHubDialog = false },
                onPlaylistsAdded = { urls ->
                    mainViewModel.refreshPlaylists(urls)
                }
            )
        }
        return
    }

    // Oynatıcı ekranındayken tam ekran göster, geri tuşunda geldiği rotaya dön
    if (currentRoute == NavRoute.PLAYER) {
        TvPlayerScreen(
            viewModel = playerViewModel,
            onBack = {
                val cur = playerViewModel.currentChannel.value
                if (cur != null) {
                    mainViewModel.lastFocusedChannelId = cur.id
                }
                currentRoute = when (playerReturnRoute) {
                    NavRoute.MOVIE_DETAIL -> if (movieDetail != null) NavRoute.MOVIE_DETAIL else NavRoute.MOVIES
                    NavRoute.DETAIL -> if (seriesDetail != null) NavRoute.DETAIL else NavRoute.SERIES
                    NavRoute.IPTV -> NavRoute.IPTV
                    NavRoute.HISTORY -> NavRoute.HISTORY
                    NavRoute.HOME -> NavRoute.HOME
                    else -> NavRoute.MOVIES
                }
            }
        )
        return
    }

    // Tam Ekran İçerik Alanı (Yan menü rayı kaldırıldı, %100 ekran genişliği)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
    ) {
        when (currentRoute) {
            NavRoute.IPTV -> TvIptvScreen(
                viewModel = mainViewModel,
                onChannelClick = { channel, sourceIndex ->
                    mainViewModel.lastFocusedChannelId = channel.id
                    tv.newtv.player.LivePlayerActivity.start(
                        context = context,
                        channel = channel,
                        sourceIndex = sourceIndex,
                        allChannels = mainViewModel.displayChannels.value
                    )
                },
                onBackToHome = { currentRoute = NavRoute.HOME }
            )
            NavRoute.HISTORY -> TvWatchHistoryScreen(
                playerViewModel = playerViewModel,
                favorites = favorites,
                onResume = { history ->
                    playerReturnRoute = NavRoute.HISTORY
                    playerViewModel.resume(history)
                    currentRoute = NavRoute.PLAYER
                },
                onFavoriteClick = { favorite ->
                    if (favorite.isMovie) vodViewModel.openMovie(tv.newtv.data.models.MovieItem(favorite.title, favorite.url, favorite.posterUrl))
                    else vodViewModel.openSeries(tv.newtv.data.models.SeriesItem(favorite.title, favorite.url, favorite.posterUrl))
                    currentRoute = if (favorite.isMovie) NavRoute.MOVIE_DETAIL else NavRoute.DETAIL
                },
                onBackToHome = { currentRoute = NavRoute.HOME }
            )
            NavRoute.MOVIES -> TvVodScreen(
                title = "Filmler",
                movies = true,
                viewModel = vodViewModel,
                onMovieClick = {
                    detailReturnRoute = NavRoute.MOVIES
                    vodViewModel.openMovie(it)
                },
                onSeriesClick = {},
                onBackToHome = { currentRoute = NavRoute.HOME }
            )
            NavRoute.SERIES -> TvVodScreen(
                title = "Diziler",
                movies = false,
                viewModel = vodViewModel,
                onMovieClick = {},
                onSeriesClick = {
                    detailReturnRoute = NavRoute.SERIES
                    vodViewModel.openSeries(it)
                },
                onBackToHome = { currentRoute = NavRoute.HOME }
            )
            NavRoute.MOVIE_DETAIL -> {
                movieDetail?.let { detail ->
                    TvMovieDetailScreen(
                        movieDetail = detail,
                        onPlayClick = { preferDubbing -> vodViewModel.playMovie(detail, preferDubbing) },
                        isFavorite = detail.sourceUrl in favoriteUrls,
                        onToggleFavorite = { vodViewModel.toggleFavorite(detail.sourceUrl, detail.title, detail.posterUrl, true) },
                        onBack = {
                            vodViewModel.clearMovieDetail()
                            currentRoute = detailReturnRoute
                        }
                    )
                }
            }
            NavRoute.DETAIL -> seriesDetail?.let { detail ->
                TvDetailScreen(
                    seriesDetail = detail,
                    onEpisodeClick = { episode, preferDubbing -> vodViewModel.openEpisode(episode.url, episode.name, preferDubbing) },
                    isFavorite = detail.sourceUrl in favoriteUrls,
                    onToggleFavorite = { vodViewModel.toggleFavorite(detail.sourceUrl, detail.title, detail.posterUrl, false) },
                    onBack = {
                        vodViewModel.clearSeriesDetail()
                        currentRoute = detailReturnRoute
                    }
                )
            }
            else -> {}
        }
        if (currentRoute == NavRoute.MOVIE_DETAIL && (vodBusy || vodError != null)) {
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.9f)).padding(24.dp)) {
                Text(if (vodBusy) "Film için açılabilir kaynak aranıyor…" else vodError.orEmpty(),
                    color = if (vodBusy) Color.White else Color(0xFFFF6060))
            }
        }
    }



    if (showGitHubDialog) {
        TvGitHubScannerDialog(
            onDismiss = { showGitHubDialog = false },
            onPlaylistsAdded = { urls ->
                mainViewModel.refreshPlaylists(urls)
            }
        )
    }
}
