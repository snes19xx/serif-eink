package com.snes19xx.einklauncher

import android.content.ContentUris
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipFile

// Manager for fetching and handling PDF/ePub files and thumbnails
class LibraryManager(private val context: Context) {

    fun fetchRecentPdfs(): List<PdfInfo> {
        val pdfs = mutableListOf<PdfInfo>()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

            var count = 0
            while (cursor.moveToNext() && count < 6) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val dateAdded = cursor.getLong(dateCol)

                val contentUri = ContentUris.withAppendedId(collection, id)
                val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(dateAdded * 1000))

                val cacheStr = "recent_${id}"
                val coverFile = File(context.filesDir, "covers/${cacheStr.hashCode()}.png")
                var thumbnail: Bitmap? = null

                if (coverFile.exists()) {
                    thumbnail = BitmapFactory.decodeFile(coverFile.absolutePath)
                } else {
                    thumbnail = generatePdfThumbnail(contentUri)
                    if (thumbnail != null) {
                        try {
                            val coversDir = File(context.filesDir, "covers")
                            if (!coversDir.exists()) coversDir.mkdirs()
                            coverFile.outputStream().use {
                                thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        } catch (e: Exception) {}
                    }
                }

                pdfs.add(PdfInfo(name, dateStr, contentUri, thumbnail))
                count++
            }
        }
        return pdfs
    }

    fun fetchLibraryBooks(): List<BookInfo> {
        val books = mutableListOf<BookInfo>()
        val libraryDir = File(Environment.getExternalStorageDirectory(), "snes_library")

        if (!libraryDir.exists()) {
            libraryDir.mkdirs()
            return books
        }

        val files = libraryDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".pdf", true) || file.name.endsWith(".epub", true))
        }

        files?.sortedByDescending { file ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
                } catch (e: Exception) {
                    file.lastModified()
                }
            } else {
                file.lastModified()
            }
        }?.forEach { file ->
            val ext = file.extension.uppercase()
            val nameWithoutExt = file.nameWithoutExtension

            val parts = nameWithoutExt.split(" - ", limit = 2)
            val title = if (parts.size == 2) parts[1] else nameWithoutExt
            val author = if (parts.size == 2) parts[0] else "Unknown Author"

            val coverFile = File(context.filesDir, "covers/${file.absolutePath.hashCode()}.png")
            var thumbnail: Bitmap? = null

            if (coverFile.exists()) {
                thumbnail = BitmapFactory.decodeFile(coverFile.absolutePath)
            } else {
                thumbnail = if (ext == "PDF") generatePdfThumbnailFromFile(file) else extractEpubCover(file.absolutePath)
                if (thumbnail != null) {
                    try {
                        val coversDir = File(context.filesDir, "covers")
                        if (!coversDir.exists()) coversDir.mkdirs()
                        coverFile.outputStream().use {
                            thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } catch (e: Exception) {}
                }
            }

            books.add(BookInfo(title, author, ext, file.absolutePath, thumbnail))
        }
        return books
    }

    fun saveCustomCover(bookPath: String, uri: Uri) {
        try {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return

            val dest = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dest)
            val cm = ColorMatrix().also { it.setSaturation(0f) }
            val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            canvas.drawBitmap(bmp, 0f, 0f, paint)

            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val outFile = File(coversDir, "${bookPath.hashCode()}.png")
            outFile.outputStream().use {
                dest.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Exception) {}
    }

    private fun generatePdfThumbnail(uri: Uri): Bitmap? {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                val renderer = PdfRenderer(fd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)

                    val renderWidth = 800
                    val renderHeight = (800.0 * page.height / page.width).toInt()
                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()

                    val cropX = (renderWidth * 0.10).toInt()
                    val cropY = (renderHeight * 0.12).toInt()
                    val cropWidth = (renderWidth * 0.80).toInt()
                    val cropHeight = (cropWidth * (80.0 / 56.0)).toInt()

                    val safeHeight = if (cropY + cropHeight > renderHeight) renderHeight - cropY else cropHeight
                    val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, safeHeight)

                    val cm = ColorMatrix().also { it.setSaturation(0f) }
                    cm.postConcat(ColorMatrix(floatArrayOf(
                        1.5f, 0f, 0f, 0f, -50f,
                        0f, 1.5f, 0f, 0f, -50f,
                        0f, 0f, 1.5f, 0f, -50f,
                        0f, 0f, 0f, 1f, 0f
                    )))
                    val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                    val finalBitmap = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
                    Canvas(finalBitmap).drawBitmap(cropped, 0f, 0f, paint)

                    return finalBitmap
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun generatePdfThumbnailFromFile(file: File): Bitmap? {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                val renderer = PdfRenderer(fd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)

                    val renderWidth = 800
                    val renderHeight = (800.0 * page.height / page.width).toInt()
                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()

                    val cropX = (renderWidth * 0.10).toInt()
                    val cropY = (renderHeight * 0.12).toInt()
                    val cropWidth = (renderWidth * 0.80).toInt()
                    val cropHeight = (cropWidth * (80.0 / 56.0)).toInt()

                    val safeHeight = if (cropY + cropHeight > renderHeight) renderHeight - cropY else cropHeight
                    val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, safeHeight)

                    val cm = ColorMatrix().also { it.setSaturation(0f) }
                    cm.postConcat(ColorMatrix(floatArrayOf(
                        1.5f, 0f, 0f, 0f, -50f,
                        0f, 1.5f, 0f, 0f, -50f,
                        0f, 0f, 1.5f, 0f, -50f,
                        0f, 0f, 0f, 1f, 0f
                    )))
                    val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                    val finalBitmap = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
                    Canvas(finalBitmap).drawBitmap(cropped, 0f, 0f, paint)

                    return finalBitmap
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun extractEpubCover(path: String): Bitmap? {
        try {
            val zip = ZipFile(path)
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
            val containerString = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }

            val opfMatcher = "full-path=\"([^\"]+)\"".toRegex().find(containerString)
            val opfPath = opfMatcher?.groupValues?.get(1) ?: return null

            val opfEntry = zip.getEntry(opfPath) ?: return null
            val opfString = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }

            val coverIdMatcher = "<meta[^>]+name=\"cover\"[^>]+content=\"([^\"]+)\"".toRegex().find(opfString)
            val coverId = coverIdMatcher?.groupValues?.get(1)

            val href = if (coverId != null) {
                "<item[^>]+id=\"$coverId\"[^>]+href=\"([^\"]+)\"".toRegex().find(opfString)?.groupValues?.get(1)
            } else {
                "<item[^>]+(properties=\"cover-image\"|id=\"cover\")[^>]+href=\"([^\"]+)\"".toRegex().find(opfString)?.groupValues?.get(2)
            } ?: return null

            val opfDir = File(opfPath).parent ?: ""
            val coverPath = if (opfDir.isEmpty()) href else "$opfDir/$href".removePrefix("/")

            val coverEntry = zip.getEntry(coverPath) ?: return null
            val coverBmp = BitmapFactory.decodeStream(zip.getInputStream(coverEntry))
            zip.close()

            val cm = ColorMatrix().also { it.setSaturation(0f) }
            val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            val greyBitmap = Bitmap.createBitmap(coverBmp.width, coverBmp.height, Bitmap.Config.ARGB_8888)
            Canvas(greyBitmap).drawBitmap(coverBmp, 0f, 0f, paint)

            return greyBitmap
        } catch (e: Exception) { }
        return null
    }
}
