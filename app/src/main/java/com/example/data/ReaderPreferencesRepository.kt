package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.AppThemeMode
import com.example.engine.LiveHighlightStyle
import com.example.engine.LiveUnitMode
import com.example.engine.PageTransitionStyle
import com.example.engine.ReaderFont
import com.example.engine.ReaderTextAlign
import com.example.engine.ReaderTheme
import com.example.engine.ReadingMode
import com.example.engine.TapNavigationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_prefs")

data class ReaderPreferences(
    val readerTheme: ReaderTheme = ReaderTheme.PAPER,
    val readerFont: ReaderFont = ReaderFont.LITERATA,
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.6f,
    val horizontalMarginDp: Int = 22,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL_PAGES,
    val pageTransitionStyle: PageTransitionStyle = PageTransitionStyle.CURL_3D,
    val pageTransitionDurationMs: Int = 460,
    val tapNavigationMode: TapNavigationMode = TapNavigationMode.VERTICAL_ZONES,
    val textAlign: ReaderTextAlign = ReaderTextAlign.JUSTIFY,
    val ambientParticlesEnabled: Boolean = true,
    val liveEnabled: Boolean = false,
    val liveUnitMode: LiveUnitMode = LiveUnitMode.WORD,
    val liveWpm: Int = 180,
    val livePhraseTempoMultiplier: Float = 1.15f,
    val liveScaleFactor: Float = 1.24f,
    val liveHighlightStyle: LiveHighlightStyle = LiveHighlightStyle.LIQUID_TRAIL,
    val liveAutoTurnPages: Boolean = true,
    val livePunctuationPause: Boolean = true,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val hasSeededLibrary: Boolean = false
)

class ReaderPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("reader_theme")
        val FONT = stringPreferencesKey("reader_font")
        val FONT_SIZE = floatPreferencesKey("font_size_sp")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val MARGIN_DP = intPreferencesKey("margin_dp")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val PAGE_TRANSITION = stringPreferencesKey("page_transition_style")
        val PAGE_TRANSITION_MS = intPreferencesKey("page_transition_duration_ms")
        val TAP_MODE = stringPreferencesKey("tap_navigation_mode")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val AMBIENT_PARTICLES = booleanPreferencesKey("ambient_particles_enabled")
        val LIVE_ENABLED = booleanPreferencesKey("live_mode_enabled")
        val LIVE_UNIT_MODE = stringPreferencesKey("live_unit_mode")
        val LIVE_WPM = intPreferencesKey("live_wpm")
        val LIVE_PHRASE_TEMPO = floatPreferencesKey("live_phrase_tempo_multiplier")
        val LIVE_SCALE = floatPreferencesKey("live_scale")
        val LIVE_STYLE = stringPreferencesKey("live_highlight_style")
        val LIVE_AUTO_TURN = booleanPreferencesKey("live_auto_turn_pages")
        val LIVE_PUNCT_PAUSE = booleanPreferencesKey("live_punctuation_pause")
        val APP_THEME = stringPreferencesKey("app_theme_mode")
        val SEEDED = booleanPreferencesKey("has_seeded_library")
    }

    val preferences: Flow<ReaderPreferences> = context.readerDataStore.data.map { prefs ->
        ReaderPreferences(
            readerTheme = prefs[Keys.THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                ?: ReaderTheme.PAPER,
            readerFont = prefs[Keys.FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() }
                ?: ReaderFont.LITERATA,
            fontSizeSp = (prefs[Keys.FONT_SIZE] ?: 18f).coerceIn(12f, 40f),
            lineSpacingMultiplier = (prefs[Keys.LINE_SPACING] ?: 1.6f).coerceIn(1.2f, 2.2f),
            horizontalMarginDp = (prefs[Keys.MARGIN_DP] ?: 22).coerceIn(8, 44),
            readingMode = prefs[Keys.READING_MODE]?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
                ?: ReadingMode.HORIZONTAL_PAGES,
            pageTransitionStyle = prefs[Keys.PAGE_TRANSITION]?.let {
                runCatching { PageTransitionStyle.valueOf(it) }.getOrNull()
            } ?: PageTransitionStyle.CURL_3D,
            pageTransitionDurationMs = (prefs[Keys.PAGE_TRANSITION_MS] ?: 460).coerceIn(260, 760),
            tapNavigationMode = prefs[Keys.TAP_MODE]?.let { runCatching { TapNavigationMode.valueOf(it) }.getOrNull() }
                ?: TapNavigationMode.VERTICAL_ZONES,
            textAlign = prefs[Keys.TEXT_ALIGN]?.let { runCatching { ReaderTextAlign.valueOf(it) }.getOrNull() }
                ?: ReaderTextAlign.JUSTIFY,
            ambientParticlesEnabled = prefs[Keys.AMBIENT_PARTICLES] ?: true,
            liveEnabled = prefs[Keys.LIVE_ENABLED] ?: false,
            liveUnitMode = prefs[Keys.LIVE_UNIT_MODE]?.let {
                runCatching { LiveUnitMode.valueOf(it) }.getOrNull()
            } ?: LiveUnitMode.WORD,
            liveWpm = (prefs[Keys.LIVE_WPM] ?: 180).coerceIn(60, 480),
            livePhraseTempoMultiplier = (prefs[Keys.LIVE_PHRASE_TEMPO] ?: 1.15f).coerceIn(0.6f, 2.4f),
            liveScaleFactor = (prefs[Keys.LIVE_SCALE] ?: 1.24f).coerceIn(1.08f, 1.42f),
            liveHighlightStyle = prefs[Keys.LIVE_STYLE]?.let {
                runCatching { LiveHighlightStyle.valueOf(it) }.getOrNull()
            } ?: LiveHighlightStyle.LIQUID_TRAIL,
            liveAutoTurnPages = prefs[Keys.LIVE_AUTO_TURN] ?: true,
            livePunctuationPause = prefs[Keys.LIVE_PUNCT_PAUSE] ?: true,
            appThemeMode = prefs[Keys.APP_THEME]?.let {
                runCatching { AppThemeMode.valueOf(it) }.getOrNull()
            } ?: AppThemeMode.SYSTEM,
            hasSeededLibrary = prefs[Keys.SEEDED] ?: false
        )
    }

    suspend fun setReaderTheme(theme: ReaderTheme) {
        context.readerDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setReaderFont(font: ReaderFont) {
        context.readerDataStore.edit { it[Keys.FONT] = font.name }
    }

    suspend fun setFontSize(sizeSp: Float) {
        context.readerDataStore.edit { it[Keys.FONT_SIZE] = sizeSp.coerceIn(12f, 40f) }
    }

    suspend fun setLineSpacing(multiplier: Float) {
        context.readerDataStore.edit { it[Keys.LINE_SPACING] = multiplier.coerceIn(1.2f, 2.2f) }
    }

    suspend fun setHorizontalMargin(marginDp: Int) {
        context.readerDataStore.edit { it[Keys.MARGIN_DP] = marginDp.coerceIn(8, 44) }
    }

    suspend fun setReadingMode(mode: ReadingMode) {
        context.readerDataStore.edit { it[Keys.READING_MODE] = mode.name }
    }

    suspend fun setPageTransitionStyle(style: PageTransitionStyle) {
        context.readerDataStore.edit { it[Keys.PAGE_TRANSITION] = style.name }
    }

    suspend fun setPageTransitionDurationMs(durationMs: Int) {
        context.readerDataStore.edit { it[Keys.PAGE_TRANSITION_MS] = durationMs.coerceIn(260, 760) }
    }

    suspend fun setTapNavigationMode(mode: TapNavigationMode) {
        context.readerDataStore.edit { it[Keys.TAP_MODE] = mode.name }
    }

    suspend fun setTextAlign(align: ReaderTextAlign) {
        context.readerDataStore.edit { it[Keys.TEXT_ALIGN] = align.name }
    }

    suspend fun setAmbientParticlesEnabled(enabled: Boolean) {
        context.readerDataStore.edit { it[Keys.AMBIENT_PARTICLES] = enabled }
    }

    suspend fun setLiveEnabled(enabled: Boolean) {
        context.readerDataStore.edit { it[Keys.LIVE_ENABLED] = enabled }
    }

    suspend fun setLiveUnitMode(unitMode: LiveUnitMode) {
        context.readerDataStore.edit { it[Keys.LIVE_UNIT_MODE] = unitMode.name }
    }

    suspend fun setLiveWpm(wpm: Int) {
        context.readerDataStore.edit { it[Keys.LIVE_WPM] = wpm.coerceIn(60, 480) }
    }

    suspend fun setLivePhraseTempoMultiplier(multiplier: Float) {
        context.readerDataStore.edit { it[Keys.LIVE_PHRASE_TEMPO] = multiplier.coerceIn(0.6f, 2.4f) }
    }

    suspend fun setLiveScaleFactor(scale: Float) {
        context.readerDataStore.edit { it[Keys.LIVE_SCALE] = scale.coerceIn(1.08f, 1.42f) }
    }

    suspend fun setLiveHighlightStyle(style: LiveHighlightStyle) {
        context.readerDataStore.edit { it[Keys.LIVE_STYLE] = style.name }
    }

    suspend fun setLiveAutoTurnPages(autoTurn: Boolean) {
        context.readerDataStore.edit { it[Keys.LIVE_AUTO_TURN] = autoTurn }
    }

    suspend fun setLivePunctuationPause(pause: Boolean) {
        context.readerDataStore.edit { it[Keys.LIVE_PUNCT_PAUSE] = pause }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        context.readerDataStore.edit { it[Keys.APP_THEME] = mode.name }
    }

    suspend fun markSeeded() {
        context.readerDataStore.edit { it[Keys.SEEDED] = true }
    }
}
