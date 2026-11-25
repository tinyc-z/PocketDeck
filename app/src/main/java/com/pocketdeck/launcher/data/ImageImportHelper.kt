package com.pocketdeck.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 用户自定义封面与背景图导入优化器：
 * - 根据设备实际屏幕分辨率动态计算目标最大边长（绝不写死固定像素，且不放大小图）；
 * - 两阶段安全解码（inJustDecodeBounds + inSampleSize），杜绝超大图 OOM；
 * - 自动校正 EXIF 旋转方向；
 * - 透明图保留 Alpha 通道保存为 WebP，不透明图保存为高质量 JPEG；
 * - 输入流硬限制 20MB，防止恶意或异常超大文件撑爆内存。
 */
object ImageImportHelper {
    private const val MAX_INPUT_BYTES = 20 * 1024 * 1024
    private const val JPEG_QUALITY = 88
    private const val WEBP_QUALITY = 86

    /** 按设备短边 ~85% 动态计算封面长边上限（钳制在 640..1280px）。 */
    fun computeCoverMaxEdge(screenWidthPx: Int, screenHeightPx: Int): Int {
        val shortEdge = min(screenWidthPx, screenHeightPx).coerceAtLeast(1)
        return (shortEdge * 0.85f).roundToInt().coerceIn(640, 1280)
    }

    /** 按设备屏幕长边动态计算背景图长边上限（钳制在 960..2560px）。 */
    fun computeBackgroundMaxEdge(screenWidthPx: Int, screenHeightPx: Int): Int {
        val longEdge = max(screenWidthPx, screenHeightPx).coerceAtLeast(1)
        return longEdge.coerceIn(960, 2560)
    }

    fun coverMaxEdgeFor(context: Context): Int {
        val dm = runCatching { context.resources?.displayMetrics }.getOrNull() ?: return 800
        return computeCoverMaxEdge(dm.widthPixels, dm.heightPixels)
    }

    fun backgroundMaxEdgeFor(context: Context): Int {
        val dm = runCatching { context.resources?.displayMetrics }.getOrNull() ?: return 1280
        return computeBackgroundMaxEdge(dm.widthPixels, dm.heightPixels)
    }

    /** 计算 2 的幂次采样率，保证初解尺寸不超过目标边长的 2 倍以内且不 OOM。 */
    fun computeInSampleSize(srcW: Int, srcH: Int, maxEdge: Int): Int {
        if (srcW <= 0 || srcH <= 0 || maxEdge <= 0) return 1
        val longSide = max(srcW, srcH)
        var sample = 1
        while (longSide / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        return sample
    }

    /** 计算等比缩放后的目标宽高（若原图已不超过 maxEdge 则原样返回，不放大）。 */
    fun computeScaledDimensions(srcW: Int, srcH: Int, maxEdge: Int): Pair<Int, Int> {
        if (srcW <= 0 || srcH <= 0 || maxEdge <= 0) return srcW to srcH
        val longSide = max(srcW, srcH)
        if (longSide <= maxEdge) return srcW to srcH
        val scale = maxEdge.toFloat() / longSide.toFloat()
        val dstW = (srcW * scale).roundToInt().coerceAtLeast(1)
        val dstH = (srcH * scale).roundToInt().coerceAtLeast(1)
        return dstW to dstH
    }

    /** 从 Uri 导入并按设备分辨率优化保存自定义封面。 */
    fun saveOptimizedCoverFromUri(
        context: Context,
        uri: Uri,
        destDir: File,
        filePrefix: String,
    ): File? {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
        }.getOrNull() ?: return null
        return saveOptimizedImageBytes(
            bytes = raw,
            destDir = destDir,
            filePrefix = filePrefix,
            maxEdge = coverMaxEdgeFor(context),
            preserveAlpha = true,
        )
    }

    /** 从字节数组优化保存自定义封面（供 Web 上传与在线下载共用）。 */
    fun saveOptimizedCoverBytes(
        context: Context,
        bytes: ByteArray,
        destDir: File,
        filePrefix: String,
    ): File? = saveOptimizedImageBytes(
        bytes = bytes,
        destDir = destDir,
        filePrefix = filePrefix,
        maxEdge = coverMaxEdgeFor(context),
        preserveAlpha = true,
    )

    /** 从 Uri 导入并按设备分辨率优化保存自定义背景图，同时清理旧背景文件。 */
    fun saveOptimizedBackgroundFromUri(
        context: Context,
        uri: Uri,
        destDir: File,
    ): File? {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
        }.getOrNull() ?: return null
        val saved = saveOptimizedImageBytes(
            bytes = raw,
            destDir = destDir,
            filePrefix = "bg_${System.currentTimeMillis()}",
            maxEdge = backgroundMaxEdgeFor(context),
            preserveAlpha = false,
        ) ?: return null
        // 清理旧的自定义背景文件，防止多次更换后磁盘堆积
        destDir.listFiles()?.forEach { old ->
            if (old.isFile && old.absolutePath != saved.absolutePath && old.name.startsWith("bg_")) {
                runCatching { old.delete() }
            }
        }
        return saved
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > MAX_INPUT_BYTES) return null
            baos.write(buf, 0, n)
        }
        val out = baos.toByteArray()
        return if (out.size >= 32) out else null
    }

    private fun saveOptimizedImageBytes(
        bytes: ByteArray,
        destDir: File,
        filePrefix: String,
        maxEdge: Int,
        preserveAlpha: Boolean,
    ): File? = runCatching {
        if (bytes.size < 32 || bytes.size > MAX_INPUT_BYTES) return null

        // 1. 仅读取宽高，不分配像素内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2. 按采样率安全解码
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

        // 3. EXIF 旋转校正
        val rotated = applyExifOrientation(bytes, sampled)

        // 4. 精确等比缩放到 maxEdge 以内（不放大）
        val (dstW, dstH) = computeScaledDimensions(rotated.width, rotated.height, maxEdge)
        val finalBmp = if (dstW != rotated.width || dstH != rotated.height) {
            Bitmap.createScaledBitmap(rotated, dstW, dstH, true).also {
                if (it !== rotated) rotated.recycle()
            }
        } else {
            rotated
        }

        // 5. 编码落盘：有透明通道用 WebP，不透明用 JPEG
        val useWebp = preserveAlpha && finalBmp.hasAlpha()
        val ext = if (useWebp) "webp" else "jpg"
        destDir.mkdirs()
        val target = File(destDir, "$filePrefix.$ext")
        try {
            target.outputStream().use { out ->
                val fmt = if (useWebp) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                val q = if (useWebp) WEBP_QUALITY else JPEG_QUALITY
                check(finalBmp.compress(fmt, q, out)) { "compress_failed" }
            }
        } finally {
            finalBmp.recycle()
        }
        if (target.isFile && target.length() > 0L) target else null
    }.getOrNull()

    private fun applyExifOrientation(bytes: ByteArray, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bmp
        }
        return runCatching {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).also {
                if (it !== bmp) bmp.recycle()
            }
        }.getOrDefault(bmp)
    }
}
