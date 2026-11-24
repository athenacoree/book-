package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BookEntity
import com.example.data.isHtmlGame
import com.example.engine.BookFormat
import com.example.engine.UniversalBookConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LuminaBook", appName)
  }

  @Test
  fun `html book entity is detected as interactive game`() {
    val gameBook = BookEntity(
      id = "g1",
      title = "Mini Juego",
      author = "Autor",
      originalFormat = "HTML",
      epubPath = "/tmp/g1.epub",
      originalPath = "/tmp/g1.html"
    )
    val regularBook = gameBook.copy(id = "b1", originalFormat = "EPUB", originalPath = null)
    assertTrue(gameBook.isHtmlGame)
    assertFalse(regularBook.isHtmlGame)
  }

  @Test
  fun `downloaded html game is saved as a playable local file`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val converter = UniversalBookConverter(context)
    val html = """<!doctype html><html lang="es"><head><title>Mini Juego</title></head><body><canvas id="game"></canvas></body></html>"""
    val result = converter.saveDownloadedHtmlGame(
      bookId = "test-starter-html-game",
      title = "Mini Juego",
      author = "LuminaBook",
      htmlBytes = html.toByteArray(Charsets.UTF_8)
    )

    assertEquals(BookFormat.HTML, result.originalFormat)
    val htmlFile = File(result.originalPath!!)
    assertTrue(htmlFile.isFile)
    val savedHtml = htmlFile.readText(Charsets.UTF_8)
    assertTrue(savedHtml.contains("<canvas"))
  }

  @Test
  fun `html bridge injects pause mute and persistent storage hooks`() {
    val injected = com.example.ui.components.injectLuminaGameBridge(
      rawHtml = "<html><head><title>Juego</title></head><body><canvas></canvas></body></html>",
      initiallyGranted = true,
      initiallyMuted = true,
      initiallyPaused = true
    )
    assertTrue(injected.contains("__luminaSetPaused"))
    assertTrue(injected.contains("__luminaSetMuted"))
    assertTrue(injected.contains("__luminaFlushStorage"))
    assertTrue(injected.contains("syncSaveState"))
    assertTrue(injected.contains("window.__luminaMuted = true"))
    assertTrue(injected.contains("window.__luminaPaused = true"))
  }

  @Test
  fun `repository saves and clears html game state and extracts record`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.BookDatabase.getInstance(context)
    val repo = com.example.data.BookRepository(context)
    val gameId = "test-html-save-game"
    db.bookDao().insertBook(
      BookEntity(
        id = gameId,
        title = "Juego Test",
        author = "Lumina",
        originalFormat = "HTML",
        epubPath = "/tmp/test.epub",
        originalPath = "/tmp/test.html"
      )
    )

    val (savedJson, record) = repo.saveHtmlGameState(
      bookId = gameId,
      rawSaveJson = """{"lumina_orb_best":"320","level":"4"}"""
    )
    assertTrue(savedJson.contains("320"))
    assertEquals("320 pts", record)

    val updated = repo.getBookById(gameId)!!
    assertEquals("320 pts", updated.gameBestRecord)

    repo.clearHtmlGameSaveData(gameId)
    val cleared = repo.getBookById(gameId)!!
    assertEquals("{}", cleared.gameSaveJson)
    assertEquals("", cleared.gameBestRecord)
  }
}
