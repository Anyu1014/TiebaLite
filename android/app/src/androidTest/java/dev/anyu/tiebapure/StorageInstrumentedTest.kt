package dev.anyu.tiebapure

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var namespace: String
    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        namespace = "test-${UUID.randomUUID()}-"
        store = LocalStore(context, namespace)
    }
    @After fun cleanUp() {
        store.list("saved").forEach { (_, j) ->
            val dir = j.optString("media-directory")
            if (dir.matches(Regex("[a-f0-9-]{36}"))) File(File(context.filesDir, "offline"), dir).deleteRecursively()
        }
        store.close()
        context.deleteDatabase(namespace + "reader.db")
        context.deleteSharedPreferences(namespace + "reader")
        context.deleteSharedPreferences(namespace + "credentials")
    }
    @Test fun encryptedAccountIsExcludedFromBackupAndLogoutClearsIt() {
        val account = Account("42", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        store.saveAccount(account)
        assertEquals(account, store.loadAccount())
        val raw = context.getSharedPreferences(namespace + "credentials", Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains(account.bduss))
        store.put("draft", "42:draft", JSONObject().put("text", "private draft"))
        val backup = store.backup().toString(Charsets.UTF_8)
        assertFalse(backup.contains(account.bduss)); assertFalse(backup.contains("private draft"))
        store.saveAccount(null); assertNull(store.loadAccount())
    }
    @Test fun malformedImportLeavesExistingHistoryIntact() {
        store.put("history", "42", Thread("42", "kept").json())
        val bad = """{"format":"tiebapure-android","version":1,"rows":[{"kind":"history","id":"42","data":{"id":"42"}}]}"""
        assertTrue(runCatching { store.restore(bad.toByteArray()) }.isFailure)
        assertEquals("kept", store.get("history", "42")?.getString("title"))
        val good = store.backup(); store.clear("history"); store.restore(good)
        assertEquals("kept", store.get("history", "42")?.getString("title"))
    }
    @Test fun historyAndPositionEnforceUserVisibleLimit() {
        for (i in 1..503) store.put("history", "$i", Thread("$i", "thread $i").json())
        assertEquals(500, store.list("history").size)
        assertNotNull(store.get("history", "503"))
    }
    @Test fun localMediaRejectsTraversalAndReaderCountsWholeEmoji() {
        assertNull(OfflineLibrary.safeFile(context, "../credentials.xml"))
        assertNull(OfflineLibrary.safeFile(context, "/data/local/tmp/x"))
        assertEquals(1, "👩‍👩‍👧‍👧".graphemes())
        assertEquals(2, "中文".graphemes())
    }
    @Test fun largeOfflineThreadUsesFilePayloadAndDetectsCorruption() {
        val body = "x".repeat(2300000)
        val snapshot = Thread("42", "large").json().put("posts", org.json.JSONArray().put(Post("7", 1, User("5", "fixture"), listOf(Block(0, body))).json()))
        store.put("saved", "42", snapshot)
        val header = store.list("saved").single().second
        assertTrue(header.toString().length < 2000)
        assertEquals(body, postFromJson(store.get("saved", "42")!!.getJSONArray("posts").getJSONObject(0)).blocks.first().text)
        val file = File(File(context.filesDir, "offline"), header.getString("snapshot-file"))
        file.writeText("{}")
        assertTrue(runCatching { store.get("saved", "42") }.isFailure)
    }
}
