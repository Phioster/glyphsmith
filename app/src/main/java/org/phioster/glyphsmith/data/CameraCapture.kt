package org.phioster.glyphsmith.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A destination for the system camera to write a photo into.
 *
 * `TakePicture` hands the camera app a uri and expects it to be filled — so the file has to
 * exist, and it has to be reachable by another process, which means a `FileProvider` uri
 * rather than a path.
 *
 * **No camera permission is involved.** The app never touches the camera; the system camera
 * app does, under its own permissions, and simply returns a picture. Asking for
 * `android.permission.CAMERA` here would be asking for something that is not used — the
 * live preview is the feature that genuinely needs it.
 */
object CameraCapture {

    private const val DIRECTORY = "captures"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * A fresh file and its shareable uri. Timestamped rather than reused, so a capture that
     * is cancelled cannot leave a stale photo behind for the next one to pick up.
     */
    fun destination(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "capture-$stamp.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        return file to uri
    }

    /**
     * Drops everything but the newest few captures.
     *
     * They live in the cache, so Android may clear them at any time — but it only does so
     * under pressure, and a user who takes fifty photos in a session should not be carrying
     * fifty full-size JPEGs around until then.
     */
    fun prune(context: Context, keep: Int = 3) {
        val dir = File(context.cacheDir, DIRECTORY)
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }
}
