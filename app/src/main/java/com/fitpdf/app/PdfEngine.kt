package com.fitpdf.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the visible content of a page lives, as fractions (0..1) of the page. null = blank page. */
class PageInfo(
    val index: Int,
    val widthPt: Int,
    val heightPt: Int,
    val content: RectF?,
)

/** Result of scanning the whole document. */
class Analysis(val pages: List<PageInfo>) {
    // One shared left/right crop for the whole document, so text columns line up page to page.
    val unionLeft: Float = pages.mapNotNull { it.content?.left }.minOrNull() ?: 0f
    val unionRight: Float = pages.mapNotNull { it.content?.right }.maxOrNull() ?: 1f
}

class PdfEngine private constructor(
    private val renderer: PdfRenderer,
    private val fd: android.os.ParcelFileDescriptor,
) {
    // PdfRenderer allows only one open page at a time.
    private val mutex = Mutex()
    private var closed = false

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(8 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val pageCount: Int get() = renderer.pageCount

    /** Scan every page at low resolution and find the bounding box of the non-white pixels. */
    suspend fun analyze(onProgress: (done: Int, total: Int) -> Unit): Analysis =
        withContext(Dispatchers.Default) {
            val total = renderer.pageCount
            val out = ArrayList<PageInfo>(total)
            for (i in 0 until total) {
                ensureActive()
                out += mutex.withLock { if (closed) null else scanPage(i) } ?: break
                onProgress(i + 1, total)
            }
            Analysis(out)
        }

    private fun scanPage(index: Int): PageInfo {
        renderer.openPage(index).use { page ->
            val w = SCAN_WIDTH
            val h = (w * page.height / page.width.toFloat()).roundToInt().coerceIn(1, w * 4)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val m = Matrix().apply { setScale(w / page.width.toFloat(), h / page.height.toFloat()) }
            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()

            val rows = IntArray(h)
            val cols = IntArray(w)
            // Skip a 1px border: scanner shadows and edge artifacts shouldn't count as content.
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = px[y * w + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    if (r < INK_THRESHOLD || g < INK_THRESHOLD || b < INK_THRESHOLD) {
                        rows[y]++
                        cols[x]++
                    }
                }
            }
            val top = rows.indexOfFirst { it >= MIN_INK }
            val bottom = rows.indexOfLast { it >= MIN_INK }
            val left = cols.indexOfFirst { it >= MIN_INK }
            val right = cols.indexOfLast { it >= MIN_INK }

            val box = if (top < 0 || left < 0) null else RectF(
                left / w.toFloat(),
                top / h.toFloat(),
                (right + 1) / w.toFloat(),
                (bottom + 1) / h.toFloat(),
            )
            return PageInfo(index, page.width, page.height, box)
        }
    }

    /** Render [crop] (fractions of the page) to a bitmap exactly [targetWidth] pixels wide. */
    suspend fun render(page: PageInfo, crop: RectF, targetWidth: Int): Bitmap? {
        val key = "${page.index}|${crop.left}|${crop.top}|${crop.right}|${crop.bottom}|$targetWidth"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            if (closed) return@withLock null
            withContext(Dispatchers.Default) {
                renderer.openPage(page.index).use { p ->
                    val cropW = crop.width() * p.width
                    val cropH = crop.height() * p.height
                    val scale = targetWidth / cropW
                    val h = (cropH * scale).roundToInt().coerceIn(1, MAX_BITMAP_HEIGHT)
                    val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    val m = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(-crop.left * p.width * scale, -crop.top * p.height * scale)
                    }
                    p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp)
                    bmp
                }
            }
        }
    }

    fun close() {
        runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    cache.evictAll()
                    runCatching { renderer.close() }
                    runCatching { fd.close() }
                }
            }
        }
    }

    companion object {
        private const val SCAN_WIDTH = 240
        private const val INK_THRESHOLD = 235   // pixel channel below this = not "white"
        private const val MIN_INK = 2            // ignore single stray pixels
        private const val MAX_BITMAP_HEIGHT = 8192

        // Safety margin kept around the content (fraction of page).
        const val PAD_X = 0.012f
        const val PAD_Y = 0.010f

        /**
         * Opens the PDF. A password-protected file throws [SecurityException] (no/wrong password).
         * Passwords are supported by the platform PdfRenderer only on Android 15 (API 35)+.
         */
        fun open(context: Context, uri: Uri, password: String? = null): PdfEngine {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Cannot open $uri")
            return try {
                val renderer = if (password != null && Build.VERSION.SDK_INT >= 35) {
                    PdfRenderer(fd, LoadParams.Builder().setPassword(password).build())
                } else {
                    PdfRenderer(fd)
                }
                PdfEngine(renderer, fd)
            } catch (t: Throwable) {
                runCatching { fd.close() }
                throw t
            }
        }

        /** The region of the page that should be shown. Whole page if [trim] is off. */
        fun cropFor(page: PageInfo, analysis: Analysis, trim: Boolean): RectF {
            val c = page.content
            if (!trim || c == null) return RectF(0f, 0f, 1f, 1f)
            return RectF(
                max(0f, analysis.unionLeft - PAD_X),
                max(0f, c.top - PAD_Y),
                min(1f, analysis.unionRight + PAD_X),
                min(1f, c.bottom + PAD_Y),
            )
        }
    }
}
