import com.moonkata.flonovel.desktop.reader.ChapterJumpNavigator
import com.moonkata.flonovel.desktop.text.ChapterDetector
import com.moonkata.flonovel.desktop.text.TextLoader
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Use the actual Desktop classes as an independent source of position expectations. */
fun main(args: Array<String>) {
    val project = Path.of(args.single()).toRealPath()
    val results = project.resolve(".work/results/position-probe")
    val manifest = JSONObject(Files.readString(results.resolve("manifest.json")))
    val fixtures = manifest.getJSONArray("fixtures")
    val records = JSONArray()
    for (index in 0 until fixtures.length()) {
        val fixture = fixtures.getJSONObject(index)
        val path = results.resolve(fixture.getString("name") + ".txt").normalize()
        require(path.startsWith(results))
        val loaded = TextLoader.load(path)
        val samples = fixture.getJSONArray("samples")
        val actualSamples = JSONArray()
        for (sampleIndex in 0 until samples.length()) {
            val offset = samples.getJSONObject(sampleIndex).getInt("offset")
            actualSamples.put(JSONObject().put("offset", offset)
                .put("character", String(Character.toChars(loaded.text.codePointAt(offset)))))
        }
        records.put(JSONObject().put("name", fixture.getString("name"))
            .put("text", loaded.text).put("encoding", loaded.charset.name())
            .put("utf16_units", loaded.text.length).put("samples", actualSamples))
    }
    val readerText = TextLoader.load(results.resolve("reader_long.txt")).text
    val chapters = ChapterDetector.detect(readerText)
    val chapterRows = JSONArray()
    chapters.forEach { chapterRows.put(JSONObject().put("title", it.title).put("offset", it.charOffset)) }
    val breakpoints = ChapterJumpNavigator.breakpoints(chapters, readerText.length, 4, readerText)
    val tinyText = TextLoader.load(results.resolve("reader_tiny.txt")).text
    val tinyPoints = ChapterJumpNavigator.breakpoints(ChapterDetector.detect(tinyText), tinyText.length, 4, tinyText)
    val jumpCases = JSONObject()
    val caseTexts = manifest.getJSONObject("jump_cases")
    for (name in caseTexts.keySet()) {
        val text = caseTexts.getString(name)
        jumpCases.put(name, JSONArray(ChapterJumpNavigator.breakpoints(ChapterDetector.detect(text), text.length, 4, text)))
    }
    val oracle = JSONObject().put("fixtures", records).put("reader_length", readerText.length)
        .put("chapters", chapterRows).put("breakpoints", JSONArray(breakpoints))
        .put("tiny_breakpoints", JSONArray(tinyPoints)).put("jump_cases", jumpCases)
        .put("manifest_sha256", MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(results.resolve("manifest.json"))).joinToString("") { "%02x".format(it) })
    Files.writeString(results.resolve("kotlin-oracle.json"), oracle.toString(2))
    println("KOTLIN_ORACLE fixtures=${records.length()} chapters=${chapters.size} breakpoints=${breakpoints.size}")
}
