package com.aritiq.calcnote.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aritiq.calcnote.data.db.LOCKED_FOLDER_ID
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.domain.Note
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedFolderScreen(navigator: Navigator, lockManager: LockManager) {
    val repo = koinInject<NoteRepository>()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        notes = repo.selectByFolder(LOCKED_FOLDER_ID)
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose { lockManager.lock() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locked") },
                navigationIcon = {
                    IconButton(onClick = {
                        lockManager.lock()
                        navigator.pop()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        lockManager.lock()
                        navigator.pop()
                    }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No locked notes", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(notes, key = { it.id }) { note ->
                    LockedNoteRow(note, onOpen = { navigator.navigate(Route.Editor(note.id)) })
                }
            }
        }
    }
}

@Composable
private fun LockedNoteRow(note: Note, onOpen: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        leadingContent = {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = {
            Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row {
                Text(note.content.lineSequence().firstOrNull { it.isNotBlank() } ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        },
    )
    HorizontalDivider()
}
