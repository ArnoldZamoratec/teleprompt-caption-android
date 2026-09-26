package com.arnoldcode.glassprompt.feature.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.arnoldcode.glassprompt.R
import java.io.File

/** An app the result screen offers a direct share button for. */
data class ShareTarget(val id: String, @param:StringRes val label: Int, val packageName: String)

/**
 * Plain ACTION_SEND sharing through the FileProvider. Direct buttons are the same intent with a
 * package set; they are only offered for installed apps, and fall back to the system sheet.
 */
object VideoSharing {

    private val Candidates = listOf(
        ShareTarget("instagram", R.string.share_instagram, "com.instagram.android"),
        ShareTarget("tiktok", R.string.share_tiktok, "com.zhiliaoapp.musically"),
        ShareTarget("tiktok", R.string.share_tiktok, "com.ss.android.ugc.trill"),
        ShareTarget("youtube", R.string.share_youtube, "com.google.android.youtube"),
        ShareTarget("whatsapp", R.string.share_whatsapp, "com.whatsapp"),
    )

    /** Installed targets, one per app (TikTok ships under two package names). */
    fun installedTargets(context: Context): List<ShareTarget> =
        Candidates.filter { context.isInstalled(it.packageName) }.distinctBy { it.id }

    fun shareVideo(context: Context, path: String, target: ShareTarget? = null, chooserTitle: String) =
        share(context, File(path), "video/mp4", target?.packageName, chooserTitle)

    fun shareSubtitles(context: Context, path: String, chooserTitle: String) =
        share(context, File(path), "application/x-subrip", null, chooserTitle)

    private fun share(context: Context, file: File, mimeType: String, packageName: String?, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val direct = packageName?.let { Intent(send).setPackage(it) }
        try {
            context.startActivity(direct ?: Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // The app can't take this file type after all: let the user pick another.
            context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun Context.isInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
