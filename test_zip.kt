import java.util.zip.ZipInputStream
import java.io.FileInputStream
import java.io.File

fun main() {
    val file = File("podcasts_backup.db")
    println(file.exists())
}
