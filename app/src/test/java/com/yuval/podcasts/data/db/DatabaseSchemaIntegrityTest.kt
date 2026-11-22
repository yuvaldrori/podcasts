package com.yuval.podcasts.data.db

import com.yuval.podcasts.di.DatabaseModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private fun stripComments(source: String): String {
        val withoutBlock = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        return withoutBlock.replace(Regex("//[^\\n]*"), "")
    }

    private fun getDeclaredDatabaseVersion(): Int {
        val file = getAppDatabaseFile()
        assertTrue("AppDatabase.kt file must exist at ${file.absolutePath}", file.exists())
        val text = stripComments(file.readText())
        val match = Regex("""@Database\s*\([^)]*?\bversion\s*=\s*(\d+)""", RegexOption.DOT_MATCHES_ALL).find(text)
        assertNotNull("version must be declared in AppDatabase.kt @Database annotation", match)
        return match!!.groupValues[1].toInt()
    }

    private fun getDeclaredAutoMigrationPairs(): Set<Pair<Int, Int>> {
        val file = getAppDatabaseFile()
        val text = stripComments(file.readText())
        val matches = Regex("""AutoMigration\s*\(\s*from\s*=\s*(\d+)\s*,\s*to\s*=\s*(\d+)[^)]*\)""").findAll(text)
        return matches.map {
            Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }.toSet()
    }

    private fun getManualMigrationPairs(): Set<Pair<Int, Int>> {
        return setOf(
            DatabaseModule.MIGRATION_5_6.startVersion to DatabaseModule.MIGRATION_5_6.endVersion,
            DatabaseModule.MIGRATION_8_9.startVersion to DatabaseModule.MIGRATION_8_9.endVersion
        )
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
            "Declared AppDatabase version ($declaredVersion) must match the highest exported Room schema version ($highestExportedVersion).",
            declaredVersion,
            highestExportedVersion
        )
    }

    @Test
    fun latestVersionTransition_hasMigrationPath() {
        val declaredVersion = getDeclaredDatabaseVersion()
        val previousVersion = declaredVersion - 1

        val allMigrationPairs = getDeclaredAutoMigrationPairs() + getManualMigrationPairs()
        val targetPair = Pair(previousVersion, declaredVersion)

        assertTrue(
            "Missing AutoMigration or manual Migration from $previousVersion to $declaredVersion!",
            allMigrationPairs.contains(targetPair)
        )
    }

    @Test
    fun latestPodcastTableSchema_containsEtagAndLastModifiedColumns() {
        val declaredVersion = getDeclaredDatabaseVersion()
        val schemaFile = File(getSchemasDir(), "$declaredVersion.json")
        assertTrue("Schema file $declaredVersion.json must exist at ${schemaFile.absolutePath}", schemaFile.exists())

        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val entities = root.getValue("database").jsonObject.getValue("entities").jsonArray

        val podcastsEntity = entities
            .map { it.jsonObject }
            .firstOrNull { it.getValue("tableName").jsonPrimitive.content == "podcasts" }
        assertNotNull("podcasts table must exist in schema $declaredVersion.json", podcastsEntity)

        val columnNames = podcastsEntity!!.getValue("fields").jsonArray
            .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
            .toSet()

        assertTrue("podcasts table in schema $declaredVersion.json must contain 'etag' column", columnNames.contains("etag"))
        assertTrue("podcasts table in schema $declaredVersion.json must contain 'lastModified' column", columnNames.contains("lastModified"))
    }
}
