package com.episodicreader.platform

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.episodicreader.domain.importer.FileInspection
import java.io.File

/**
 * Platform helper for Android Storage Access Framework (SAF) URIs.
 * Never requests broad external storage permissions; reads strictly via ContentResolver.
 */
object SafDocumentHelper {
    val SUPPORTED_MIME_TYPES: Array<String> = arrayOf(
        "application/epub+zip",
        "application/pdf",
        "application/octet-stream",
    )

    fun inspectUri(context: Context, uri: Uri, targetDir: File): FileInspection {
        val resolver = context.contentResolver
        var displayName = "Imported Book"
        var fileSize = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

        val header = ByteArray(16)
        var headerRead = 0
        resolver.openInputStream(uri)?.use { stream ->
            headerRead = stream.read(header).coerceAtLeast(0)
            if (fileSize <= 0L) {
                var counted = headerRead.toLong()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    counted += n
                }
                fileSize = counted
            }
        }

        val statFs = StatFs(targetDir.apply { mkdirs() }.absolutePath)
        val availableBytes = statFs.availableBytes

        return FileInspection(
            displayName = displayName,
            mimeType = resolver.getType(uri),
            fileSizeBytes = fileSize,
            headerBytes = header.copyOf(headerRead),
            availableStorageBytes = availableBytes,
        )
    }
}
