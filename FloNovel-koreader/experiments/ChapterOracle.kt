import com.moonkata.flonovel.desktop.text.ChapterDetector
import com.moonkata.flonovel.desktop.text.ChapterPatterns
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Characterize custom patterns using the existing Desktop implementation. */
fun main(args: Array<String>) {
    val root = Path.of(args.single()).resolve(".work/results/focused")
    val input = root.resolve("chapter-cases.json")
    val cases = JSONObject(Files.readString(input)).getJSONArray("cases")
    val rows = JSONArray()
    for (index in 0 until cases.length()) {
        val case = cases.getJSONObject(index)
        val rules = ChapterPatterns.buildRules(
            enabledPresetIds = if (case.optBoolean("preset")) setOf("hash") else emptySet(),
            customPatterns = if (case.optBoolean("preset")) emptyList() else listOf(case.getString("pattern")),
        )
        val chapters = ChapterDetector.detect(case.getString("text"), rules)
        rows.put(JSONObject().put("name", case.getString("name")).put("rule_count", rules.size)
            .put("chapters", JSONArray(chapters.map { JSONObject().put("title", it.title).put("offset", it.charOffset) })))
    }
    val output = JSONObject().put("cases", rows).put("manifest_sha256", MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(input)).joinToString("") { "%02x".format(it) })
    Files.writeString(root.resolve("chapter-oracle.json"), output.toString(2))
    println("CHAPTER_KOTLIN_CASES ${rows.length()}")
}
