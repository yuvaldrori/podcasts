package com.yuval.podcasts.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseSchemaIntegrityTest {

    private fun getSchemasDir(): File {
        val path1 = File("schemas/com.yuval.podcasts.data.db.AppDatabase")
        if (path1.exists()) return path1
        val path2 = File("app/schemas/com.yuval.podcasts.data.db.AppDatabase")
        if (path2.exists()) return path2
        return path1
    }

    private fun getAppDatabaseFile(): File {
        val path1 = File("src/main/java/com/yuval/podcasts/data/db/AppDatabase.kt")
        if (path1.exists()) return path1
        val path2 = File("app/src/main/java/com/yuval/podcasts/data/db/AppDatabase.kt")
        if (path2.exists()) return path2
        return path1
    }

    private fun getDeclaredDatabaseVersion(): Int {
        val file = getAppDatabaseFile()
        assertTrue("AppDatabase.kt file must exist at ${file.absolutePath}", file.exists())
        val text = file.readText()
        val match = Regex("""version\s*=\s*(\d+)""").find(text)
        assertNotNull("version must be declared in AppDatabase.kt @Database annotation", match)
        return match!!.groupValues[1].toInt()
    }

    private fun getDeclaredAutoMigrationPairs(): Set<Pair<Int, Int>> {
        val file = getAppDatabaseFile()
        val text = file.readText()
        val matches = Regex("""AutoMigration\s*\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)\s*\)""").findAll(text)
        return matches.map {
            Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }.toSet()
    }

    @Test
    fun databaseVersion_matchesHighestExportedSchema() {
        val schemasDir = getSchemasDir()
        assertTrue("Schemas directory must exist at ${schemasDir.absolutePath}", schemasDir.exists())

        val schemaFiles = schemasDir.listFiles { _, name -> name.endsWith(".json") }
        assertNotNull("Schema files must not be null", schemaFiles)
        assertTrue("At least one schema JSON file must exist", schemaFiles!!.isNotEmpty())

        val exportedVersions = schemaFiles.mapNotNull { file ->
            file.nameWithoutExtension.toIntOrNull()
        }.sorted()

        val highestExportedVersion = exportedVersions.last()
        val declaredVersion = getDeclaredDatabaseVersion()

        assertEquals(
            "Declared AppDatabase version ($declaredVersion) must match the highest exported Room schema version ($highestExportedVersion). " +
                    "If you changed an @Entity or DB schema, update version in AppDatabase.kt!",
            declaredVersion,
            highestExportedVersion
        )
    }

    @Test
    fun latestVersionTransition_hasAutoMigration() {
        val declaredVersion = getDeclaredDatabaseVersion()
        val previousVersion = declaredVersion - 1

        val autoMigrationPairs = getDeclaredAutoMigrationPairs()
        val targetPair = Pair(previousVersion, declaredVersion)

        assertTrue(
            "Missing AutoMigration(from = $previousVersion, to = $declaredVersion) in AppDatabase.kt! " +
                    "When bumping database version to $declaredVersion, you must add an AutoMigration to prevent app crashes on upgraded devices.",
            autoMigrationPairs.contains(targetPair)
        )
    }

    @Test
    fun podcastTableSchemaVersion10_containsEtagAndLastModifiedColumns() {
        val schemasDir = getSchemasDir()
        val schema10File = File(schemasDir, "10.json")
        assertTrue("Schema file 10.json must exist at ${schema10File.absolutePath}", schema10File.exists())

        val jsonText = schema10File.readText()

        assertTrue("podcasts table in schema 10.json must contain 'etag' field", jsonText.contains("\"fieldPath\": \"etag\""))
        assertTrue("podcasts table in schema 10.json must contain 'lastModified' field", jsonText.contains("\"fieldPath\": \"lastModified\""))
    }
}
