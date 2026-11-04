import com.moonkata.flonovel.desktop.library.RelativePath
import com.moonkata.flonovel.desktop.sync.ContentHash
import com.moonkata.flonovel.desktop.sync.DropboxOAuth
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Independent expectations from existing classes; no credentials or requests. */
fun main(args: Array<String>) {
    val root = Path.of(args.single()).resolve(".work/results/finish")
    val input = root.resolve("contracts-input.json")
    val data = JSONObject(Files.readString(input))
    val paths = JSONArray()
    for (index in 0 until data.getJSONArray("paths").length()) {
        val raw = data.getJSONArray("paths").getString(index)
        paths.put(JSONObject().put("input", raw).put("key", RelativePath.normalize(raw)))
    }
    val hashes = JSONArray()
    for (index in 0 until data.getJSONArray("files").length()) {
        val name = data.getJSONArray("files").getString(index)
        hashes.put(JSONObject().put("name", name).put("hash", ContentHash.of(root.resolve(name))))
    }
    val result = JSONObject().put("paths", paths).put("files", hashes)
        .put("pkce", DropboxOAuth.generateCodeChallenge(data.getString("verifier")))
        .put("java_version", System.getProperty("java.version"))
        .put("manifest_sha256", MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(input)).joinToString("") { "%02x".format(it) })
    Files.writeString(root.resolve("contracts-oracle.json"), result.toString(2))
    println("CONTRACT_ORACLE paths=${paths.length()} hashes=${hashes.length()}")
}
