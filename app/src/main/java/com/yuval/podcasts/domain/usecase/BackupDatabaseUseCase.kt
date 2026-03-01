package com.yuval.podcasts.domain.usecase

import android.content.Context
import com.yuval.podcasts.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.system.exitProcess

class BackupDatabaseUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    suspend operator fun invoke(outputStream: OutputStream): Nothing = withContext(Dispatchers.IO) {
        database.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        database.close()
        
        val dbFile = context.getDatabasePath("podcasts_db")
        val walFile = context.getDatabasePath("podcasts_db-wal")
        val shmFile = context.getDatabasePath("podcasts_db-shm")

        ZipOutputStream(outputStream).use { zipOut ->
            listOf(dbFile, walFile, shmFile).forEach { file ->
                if (file.exists()) {
                    zipOut.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
        exitProcess(0)
    }
}
