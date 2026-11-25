package com.example.data
import org.json.JSONObject
import com.example.engine.*
object BookReaderSettingsCodec {
 fun encode(p: ReaderPreferences): String = JSONObject().apply {
  put("readerTheme",p.readerTheme.name); put("readerFont",p.readerFont.name); put("fontSizeSp",p.fontSizeSp.toDouble())
  put("lineSpacingMultiplier",p.lineSpacingMultiplier.toDouble()); put("horizontalMarginDp",p.horizontalMarginDp)
  put("readingMode",p.readingMode.name); put("pageTransitionStyle",p.pageTransitionStyle.name); put("pageTransitionDurationMs",p.pageTransitionDurationMs)
  put("tapNavigationMode",p.tapNavigationMode.name); put("textAlign",p.textAlign.name); put("ambientParticlesEnabled",p.ambientParticlesEnabled)
  put("liveEnabled",p.liveEnabled); put("liveUnitMode",p.liveUnitMode.name); put("liveWpm",p.liveWpm)
  put("livePhraseTempoMultiplier",p.livePhraseTempoMultiplier.toDouble()); put("liveScaleFactor",p.liveScaleFactor.toDouble())
  put("liveHighlightStyle",p.liveHighlightStyle.name); put("liveAutoTurnPages",p.liveAutoTurnPages); put("livePunctuationPause",p.livePunctuationPause)
 }.toString()
 fun decode(json: String, d: ReaderPreferences): ReaderPreferences {
  if(json.isBlank()) return d
  val o=runCatching { JSONObject(json) }.getOrNull() ?: return d
  return d.copy(readerTheme=e(o.optString("readerTheme"),d.readerTheme),readerFont=e(o.optString("readerFont"),d.readerFont),
   fontSizeSp=o.optDouble("fontSizeSp",d.fontSizeSp.toDouble()).toFloat().coerceIn(12f,40f),
   lineSpacingMultiplier=o.optDouble("lineSpacingMultiplier",d.lineSpacingMultiplier.toDouble()).toFloat().coerceIn(1.2f,2.2f),
   horizontalMarginDp=o.optInt("horizontalMarginDp",d.horizontalMarginDp).coerceIn(8,44),readingMode=e(o.optString("readingMode"),d.readingMode),
   pageTransitionStyle=e(o.optString("pageTransitionStyle"),d.pageTransitionStyle),pageTransitionDurationMs=o.optInt("pageTransitionDurationMs",d.pageTransitionDurationMs).coerceIn(260,760),
   tapNavigationMode=e(o.optString("tapNavigationMode"),d.tapNavigationMode),textAlign=e(o.optString("textAlign"),d.textAlign),
   ambientParticlesEnabled=o.optBoolean("ambientParticlesEnabled",d.ambientParticlesEnabled),liveEnabled=o.optBoolean("liveEnabled",d.liveEnabled),
   liveUnitMode=e(o.optString("liveUnitMode"),d.liveUnitMode),liveWpm=o.optInt("liveWpm",d.liveWpm).coerceIn(60,480),
   livePhraseTempoMultiplier=o.optDouble("livePhraseTempoMultiplier",d.livePhraseTempoMultiplier.toDouble()).toFloat().coerceIn(0.6f,2.4f),
   liveScaleFactor=o.optDouble("liveScaleFactor",d.liveScaleFactor.toDouble()).toFloat().coerceIn(1.08f,1.42f),
   liveHighlightStyle=e(o.optString("liveHighlightStyle"),d.liveHighlightStyle),liveAutoTurnPages=o.optBoolean("liveAutoTurnPages",d.liveAutoTurnPages),
   livePunctuationPause=o.optBoolean("livePunctuationPause",d.livePunctuationPause))
 }
 private inline fun <reified T:Enum<T>> e(v:String,d:T):T = enumValues<T>().firstOrNull { it.name==v } ?: d
}