package com.yuval.podcasts.domain.usecase

import android.content.Context
import com.yuval.podcasts.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlin.system.exitProcess

class RestoreDatabaseUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    suspend operator fun invoke(inputStream: InputStream): Nothing = withContext(Dispatchers.IO) {
        database.close()
        
        ZipInputStream(inputStream).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val dbFile = context.getDatabasePath(entry.name)
                dbFile.outputStream().use { zipIn.copyTo(it) }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        exitProcess(0)
    }
}
