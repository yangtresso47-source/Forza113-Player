package com.kuqforza.iptv.web

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.kuqforza.data.local.KuqforzaDatabase
import com.kuqforza.data.local.entity.ProviderEntity
import com.kuqforza.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

object PortalImporter {
    private const val TAG = "PortalImporter"

    suspend fun importPending(context: Context) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "pending_imports.json")
            if (!file.exists()) return@withContext

            try {
                val pending = JSONArray(file.readText())
                var imported = 0
                var changed = false

                val db = Room.databaseBuilder(
                    context.applicationContext,
                    KuqforzaDatabase::class.java,
                    "kuqforza.db"
                ).fallbackToDestructiveMigration().build()

                val dao = db.providerDao()

                for (i in 0 until pending.length()) {
                    val p = pending.getJSONObject(i)
                    if (p.optBoolean("imported", false)) continue

                    val type = p.optString("type", "xtream")
                    val providerType = when (type) {
                        "xtream" -> ProviderType.XTREAM_CODES
                        "m3u" -> ProviderType.M3U
                        "stalker" -> ProviderType.STALKER_PORTAL
                        else -> ProviderType.XTREAM_CODES
                    }

                    val entity = ProviderEntity(
                        name = p.optString("name", "Imported"),
                        type = providerType,
                        serverUrl = p.optString("server", ""),
                        username = p.optString("username", ""),
                        password = p.optString("password", ""),
                        m3uUrl = p.optString("m3u_url", ""),
                        stalkerMacAddress = p.optString("mac", ""),
                        isActive = true
                    )

                    try {
                        dao.insert(entity)
                        p.put("imported", true)
                        changed = true
                        imported++
                        Log.i(TAG, "Imported: ${'$'}{entity.name} (${'$'}{entity.type})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed: ${'$'}{entity.name}", e)
                    }
                }

                if (changed) file.writeText(pending.toString(2))
                db.close()
                Log.i(TAG, "Imported ${'$'}imported providers")
            } catch (e: Exception) {
                Log.e(TAG, "Import error", e)
            }
        }
    }
}
