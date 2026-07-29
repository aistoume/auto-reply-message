package solutions.aicon.aplomb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Grabs one full-screen frame as a Bitmap on demand.
 *
 * Android 14+ allows exactly ONE createVirtualDisplay per MediaProjection
 * instance (SecurityException on the second) — so the VirtualDisplay +
 * ImageReader live for the whole session and each captureOnce() picks a
 * frame rendered AFTER the request (the bubble hides right before, which
 * both guarantees a fresh composition and keeps it out of the shot).
 */
class ScreenCaptureManager(
    context: Context,
    private val projection: MediaProjection,
) {
    private val metrics = DisplayMetrics().also {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(it)
    }
    private val width = metrics.widthPixels
    private val height = metrics.heightPixels
    private val density = metrics.densityDpi
    private val main = Handler(Looper.getMainLooper())

    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var pending: ((Bitmap?) -> Unit)? = null
    private var requestNanos = 0L
    private var requestUptime = 0L

    fun captureOnce(onResult: (Bitmap?) -> Unit) {
        if (pending != null) { onResult(null); return }
        if (display == null && !start()) { onResult(null); return }
        requestNanos = System.nanoTime()
        requestUptime = SystemClock.uptimeMillis()
        pending = onResult
        // Deadline: fully static screen / bogus timestamps → give up so the
        // caller can restore the bubble instead of hanging forever.
        main.postDelayed({
            if (pending === onResult) { pending = null; onResult(null) }
        }, 1000)
    }

    /** Build the session-long VirtualDisplay. False if the grant is dead. */
    private fun start(): Boolean {
        val ir = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        ir.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val cb = pending
            // Frames composed before the request may still show the bubble —
            // gate on the frame timestamp where the device provides one,
            // with a 300ms grace fallback for devices that report 0.
            val fresh = image.timestamp <= 0L || image.timestamp > requestNanos ||
                SystemClock.uptimeMillis() - requestUptime > 300
            if (cb == null || !fresh) { image.close(); return@setOnImageAvailableListener }
            pending = null
            val bmp = toBitmap(image)
            image.close()
            cb(bmp)
        }, main)
        return runCatching {
            display = projection.createVirtualDisplay(
                "aplomb-capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                ir.surface, null, main
            )
            reader = ir
        }.onFailure { ir.close() }.isSuccess
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(padded, 0, 0, width, height)
    }

    fun release() {
        pending = null
        display?.release(); display = null
        reader?.close(); reader = null
    }
}
