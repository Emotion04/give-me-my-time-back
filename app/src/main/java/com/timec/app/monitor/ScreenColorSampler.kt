package com.timec.app.monitor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object ScreenColorSampler {
    @Volatile
    var appContext: Context? = null
        private set

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0

    val isActive: Boolean get() = projection != null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun activate(resultCode: Int, resultData: Intent?) {
        release()
        val ctx = appContext ?: return
        val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        val mp = mgr.getMediaProjection(resultCode, resultData ?: return) ?: return
        projection = mp
        val metrics = ctx.resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        try {
            virtualDisplay = mp.createVirtualDisplay(
                "timec_color_sampler",
                width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )
        } catch (_: Exception) {
            release()
        }
    }

    fun release() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
    }

    /** 采样屏幕 (x,y,w,h) 区域的平均颜色（降采样取近似值） */
    fun sampleAverage(x: Int, y: Int, w: Int, h: Int): Int? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val x0 = x.coerceIn(0, (width - 1).coerceAtLeast(0))
            val y0 = y.coerceIn(0, (height - 1).coerceAtLeast(0))
            val x1 = (x + w).coerceIn(x0 + 1, width)
            val y1 = (y + h).coerceIn(y0 + 1, height)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0L
            val step = 6
            var yy = y0
            while (yy < y1) {
                var xx = x0
                while (xx < x1) {
                    val idx = yy * rowStride + xx * pixelStride
                    b += buffer.get(idx).toInt() and 0xFF
                    g += buffer.get(idx + 1).toInt() and 0xFF
                    r += buffer.get(idx + 2).toInt() and 0xFF
                    n++
                    xx += step
                }
                yy += step
            }
            if (n == 0L) return null
            return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        } catch (_: Exception) {
            return null
        } finally {
            image.close()
        }
    }
}
