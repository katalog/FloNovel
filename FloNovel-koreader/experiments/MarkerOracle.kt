import com.moonkata.flonovel.desktop.reader.ChapterJumpNavigator
import com.moonkata.flonovel.desktop.text.ChapterDetector
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Compare the requested trusted ## marker with actual existing Kotlin behavior. */
fun main(args: Array<String>) {
    val root = Path.of(args.single()).resolve(".work/results/focused")
    val input = root.resolve("marker-cases.json")
    val cases = JSONObject(Files.readString(input)).getJSONArray("cases")
    val rows = JSONArray()
    for (index in 0 until cases.length()) {
        val case = cases.getJSONObject(index)
        val text = case.getString("text")
        val chapters = ChapterDetector.detect(text)
        val points = JSONObject()
        for (divisions in listOf(1, 2, 4, 8, 16)) {
            points.put(divisions.toString(), JSONArray(ChapterJumpNavigator.breakpoints(chapters, text.length, divisions, text)))
        }
        rows.put(JSONObject().put("name", case.getString("name")).put("length", text.length)
            .put("chapters", JSONArray(chapters.map { JSONObject().put("title", it.title).put("offset", it.charOffset) }))
            .put("points", points))
    }
    val output = JSONObject().put("cases", rows).put("manifest_sha256", MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(input)).joinToString("") { "%02x".format(it) })
    Files.writeString(root.resolve("marker-oracle.json"), output.toString(2))
    println("MARKER_KOTLIN_CASES ${rows.length()}")
}
