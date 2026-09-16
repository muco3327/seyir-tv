package tv.newtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Text
import tv.newtv.data.local.WatchHistoryEntity
import tv.newtv.ui.viewmodel.TvPlayerViewModel
import tv.newtv.ui.viewmodel.TvVodViewModel

@Composable
fun TvWatchHistoryScreen(
    playerViewModel: TvPlayerViewModel,
    onResume: (WatchHistoryEntity) -> Unit,
    favorites: List<TvVodViewModel.FavoriteVod> = emptyList(),
    onFavoriteClick: (TvVodViewModel.FavoriteVod) -> Unit = {},
    onBackToHome: () -> Unit = {}
) {
    BackHandler {
        onBackToHome()
    }

    val history by playerViewModel.recentHistory.collectAsStateWithLifecycle()
    val movies = history.filter { it.isMovie }
    val series = history.filterNot { it.isMovie }

    if (history.isEmpty() && favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF13151A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("İzlemeye Devam Et", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("İzleme ilerlemeniz burada görünecek.", color = Color.LightGray, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))
                TvVodHomeBackButton(onClick = onBackToHome)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF13151A)),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                TvVodHomeBackButton(onClick = onBackToHome)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    "İzlemeye Devam Et",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (favorites.isNotEmpty()) {
            item {
                TvVodRow(
                    categoryTitle = "Favoriler",
                    isMovie = true,
                    items = favorites.map { favorite ->
                        VodRowItem(
                            title = favorite.title,
                            posterUrl = favorite.posterUrl,
                            provider = if (favorite.isMovie) "Film" else "Dizi",
                            onClick = { onFavoriteClick(favorite) }
                        )
                    }
                )
            }
        }
        if (movies.isNotEmpty()) {
            item {
                TvVodRow(
                    categoryTitle = "Filmler",
                    isMovie = true,
                    items = movies.map { historyItem ->
                        VodRowItem(
                            title = historyItem.title,
                            posterUrl = historyItem.posterUrl,
                            provider = progressLabel(historyItem),
                            onClick = { onResume(historyItem) }
                        )
                    }
                )
            }
        }
        if (series.isNotEmpty()) {
            item {
                TvVodRow(
                    categoryTitle = "Diziler",
                    isMovie = false,
                    items = series.map { historyItem ->
                        VodRowItem(
                            title = historyItem.title,
                            posterUrl = historyItem.posterUrl,
                            provider = progressLabel(historyItem),
                            onClick = { onResume(historyItem) }
                        )
                    }
                )
            }
        }
    }
}

private fun progressLabel(item: WatchHistoryEntity): String {
    if (item.durationMs <= 0L) return "Devam et"
    val percent = ((item.positionMs * 100) / item.durationMs).coerceIn(0L, 100L)
    return "%$percent izlendi"
}
