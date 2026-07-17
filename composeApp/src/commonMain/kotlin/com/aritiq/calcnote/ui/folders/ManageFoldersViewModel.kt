package com.aritiq.calcnote.ui.folders

import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.domain.Folder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ManageFoldersViewModel(
    private val folderRepo: FolderRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = _state.value.copy(folders = folderRepo.all())
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val now = Clock.System.now()
            val id = now.toEpochMilliseconds().toString(16) + "-" + (0..Int.MAX_VALUE).random().toString(16)
            folderRepo.upsert(Folder(id = id, name = name, createdAt = now, updatedAt = now))
            load()
        }
    }

    fun rename(id: String, newName: String) {
        if (newName.isBlank()) return
        scope.launch {
            val f = folderRepo.getById(id) ?: return@launch
            folderRepo.upsert(f.copy(name = newName, updatedAt = Clock.System.now()))
            load()
        }
    }

    fun delete(id: String) {
        scope.launch {
            folderRepo.delete(id)
            load()
        }
    }

    data class UiState(
        val folders: List<Folder> = emptyList(),
    )
}
