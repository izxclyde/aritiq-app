package com.aritiq.calcnote.data.repository

interface SettingsRepository {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun all(): Map<String, String>
}

class SqlDelightSettingsRepository(
    private val db: com.aritiq.calcnote.data.db.CalcNoteDatabase,
) : SettingsRepository {
    override suspend fun get(key: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.settingQueries.selectValue(key).executeAsOneOrNull()
        }
    }
    override suspend fun set(key: String, value: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.settingQueries.upsert(key, value)
        }
    }
    override suspend fun all(): Map<String, String> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.settingQueries.selectAll().executeAsList().associate { it.key to it.value_data }
        }
    }
}