package com.moonkata.flonovel.android.data.preprocess

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moonkata.flonovel.android.data.file.EncodingDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The JVM unit test cannot see what Android's ICU-backed regex engine and charsets do differently,
 * which is exactly where the port could drift from the Desktop. This runs the same parity fixtures
 * on the device.
 */
@RunWith(AndroidJUnit4::class)
class PreprocessParityDeviceTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun read(name: String) = assets.open("fixtures/parity/$name").use { it.readBytes() }

    @Test
    fun contentMatchesTheDesktopOnThisDevice() {
        val inputs = assets.list("fixtures/parity")!!.filter { it.endsWith(".input.txt") }.sorted()
        assertTrue(inputs.size >= 8)
        for (input in inputs) {
            val expected = read(input.removeSuffix(".input.txt") + ".expected.txt").toString(Charsets.UTF_8)
            val actual = TextPreprocessor.normalizeContent(EncodingDetector.decode(read(input)))
            assertEquals("$input differs from the Desktop output on this device", expected, actual)
        }
    }

    @Test
    fun fileNamesMatchTheDesktopOnThisDevice() {
        val names = read("filenames.input.tsv").toString(Charsets.UTF_8).lines().dropLast(1)
        val cleaned = names.joinToString("") { TextPreprocessor.cleanFileName(it) + "\n" }
        assertEquals(read("filenames.expected.tsv").toString(Charsets.UTF_8), cleaned)
    }
}
