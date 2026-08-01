package com.builtdifferent.audio8d.ui.screens.library

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.builtdifferent.audio8d.data.db.ConversionEntity
import com.builtdifferent.audio8d.ui.screens.home.ConversionCard
import java.io.File

@Composable
fun LibraryScreen(
    onOpenPlayer: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val items by viewModel.libraryItems.collectAsState()
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ConversionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.setQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search converted library") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(text = { Text("Newest first") }, onClick = {
                        viewModel.setSortOrder(SortOrder.NEWEST); showSortMenu = false
                    })
                    DropdownMenuItem(text = { Text("Title A-Z") }, onClick = {
                        viewModel.setSortOrder(SortOrder.TITLE_AZ); showSortMenu = false
                    })
                    DropdownMenuItem(text = { Text("File size") }, onClick = {
                        viewModel.setSortOrder(SortOrder.SIZE); showSortMenu = false
                    })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { entity ->
                Column {
                    ConversionCard(entity = entity, onClick = { onOpenPlayer(entity.id) })
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        IconButton(onClick = { viewModel.toggleFavorite(entity) }) {
                            Icon(
                                if (entity.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite"
                            )
                        }
                        IconButton(onClick = { renameTarget = entity; renameText = entity.title }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = {
                            entity.convertedFilePath?.let { path ->
                                val file = File(path)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share 8D track"))
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { viewModel.delete(entity) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { entity ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename track") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(entity, renameText)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}
