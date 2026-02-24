package com.streamvault.app.ui.screens.series

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.streamvault.app.ui.components.CategoryRow
import com.streamvault.app.ui.components.ContinueWatchingRow
import com.streamvault.app.ui.components.SeriesCard
import com.streamvault.app.ui.components.SkeletonRow
import com.streamvault.app.ui.components.TopNavBar
import com.streamvault.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.streamvault.app.R

@Composable
fun SeriesScreen(
    onSeriesClick: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingSeriesId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showPinDialog) {
        com.streamvault.app.ui.components.dialogs.PinDialog(
            onDismissRequest = {
                showPinDialog = false
                pinError = null
                pendingSeriesId = null
            },
            onPinEntered = { pin ->
                scope.launch {
                    if (viewModel.verifyPin(pin)) {
                        showPinDialog = false
                        pinError = null
                        pendingSeriesId?.let { onSeriesClick(it) }
                        pendingSeriesId = null
                    } else {
                        pinError = context.getString(R.string.series_incorrect_pin)
                    }
                }
            },
            error = pinError
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopNavBar(currentRoute = currentRoute, onNavigate = onNavigate)

        if (uiState.isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(3) {
                    SkeletonRow(
                        modifier = Modifier.fillMaxWidth(),
                        cardWidth = 140,
                        cardHeight = 210,
                        itemsCount = 10
                    )
                }
            }
        } else if (uiState.seriesByCategory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📺", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.series_no_found), style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Continue Watching row (shown first, only if non-empty)
                item(key = "continue_watching") {
                    ContinueWatchingRow(
                        items = uiState.continueWatching,
                        onItemClick = { history -> onSeriesClick(history.seriesId ?: history.contentId) }
                    )
                }
                items(
                    items = uiState.seriesByCategory.entries.toList(),
                    key = { it.key }
                ) { (categoryName, seriesList) ->
                    CategoryRow(title = categoryName, items = seriesList) { series ->
                        val isLocked = (series.isAdult || series.isUserProtected) && uiState.parentalControlLevel == 1
                        SeriesCard(
                            series = series,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    pendingSeriesId = series.id
                                    showPinDialog = true
                                } else {
                                    onSeriesClick(series.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
