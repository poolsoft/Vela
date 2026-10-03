package app.vela.carlauncher.media

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MusicRepositoryCacheTest {
    @Test fun cachedUsbLibraryLoadsAfterConstructionWithoutBlockingCaller() {
        val dir = Files.createTempDirectory("vela-music-cache").toFile()
        val diskEntered = CountDownLatch(1)
        val allowDisk = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        try {
            val track = File(dir, "test.mp3").apply { writeBytes(byteArrayOf(1)) }
            File(dir, "car_music_index_v1.json").writeText(org.json.JSONArray().put(
                org.json.JSONObject().put("id", 42L).put("baslik", "Saved track")
                    .put("dosyaYolu", track.absolutePath).put("contentUri", "file://${track.absolutePath}")
            ).toString())
            val context = object : ContextWrapper(null) {
                override fun getFilesDir(): File {
                    diskEntered.countDown()
                    check(allowDisk.await(5, TimeUnit.SECONDS))
                    return dir
                }
            }
            val constructor = MusicRepository::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }
            val repository = caller.submit<MusicRepository> { constructor.newInstance(context) }
                .get(2, TimeUnit.SECONDS)
            assertTrue(diskEntered.await(2, TimeUnit.SECONDS))
            assertTrue(repository.parcalar.value.isEmpty())
            allowDisk.countDown()
            runBlocking {
                withTimeout(3000) { while (repository.klasorler.value.isEmpty()) delay(10) }
            }
            assertEquals("Saved track", repository.parcalar.value.single().baslik)
            assertEquals(1, repository.klasorler.value.single().parcaSayisi)
        } finally {
            allowDisk.countDown()
            caller.shutdownNow()
            dir.deleteRecursively()
        }
    }
}
