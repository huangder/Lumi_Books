package com.huangder.lumibooks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.huangder.lumibooks.MainActivity
import com.huangder.lumibooks.ui.bookshelf.BookNotesActivity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun AppWidgetProvider.runWidgetUpdate(
    tag: String,
    block: suspend () -> Unit
) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (error: Throwable) {
            Log.e(tag, "Unable to update app widget", error)
        } finally {
            pendingResult.finish()
        }
    }
}

internal fun <T : AppWidgetProvider> widgetIds(
    context: Context,
    providerClass: Class<T>
): IntArray {
    return AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, providerClass)
    )
}

internal fun openBookshelfPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .setData(Uri.parse("lumibooks://widget/bookshelf/$requestCode"))
        .putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DESTINATION_BOOKSHELF)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun openBookPendingIntent(
    context: Context,
    appWidgetId: Int,
    bookId: String
): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .setData(
            Uri.Builder()
                .scheme("lumibooks")
                .authority("widget")
                .appendPath("read")
                .appendPath(bookId)
                .build()
        )
        .putExtra(MainActivity.EXTRA_OPEN_BOOK_ID, bookId)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return PendingIntent.getActivity(
        context,
        CONTINUE_REQUEST_CODE_BASE + appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun openQuotePendingIntent(
    context: Context,
    appWidgetId: Int,
    bookId: String,
    noteId: Long,
    initialTab: Int
): PendingIntent {
    val intent = Intent(context, BookNotesActivity::class.java)
        .setData(
            Uri.Builder()
                .scheme("lumibooks")
                .authority("widget")
                .appendPath("notes")
                .appendPath(bookId)
                .appendPath(noteId.toString())
                .build()
        )
        .putExtra(BookNotesActivity.EXTRA_BOOK_ID, bookId)
        .putExtra(BookNotesActivity.EXTRA_INITIAL_TAB, initialTab)
        .putExtra(BookNotesActivity.EXTRA_TARGET_NOTE_ID, noteId)
    return PendingIntent.getActivity(
        context,
        QUOTE_REQUEST_CODE_BASE + appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun decodeWidgetCover(path: String?, targetSizePx: Int): Bitmap? {
    if (path.isNullOrBlank() || targetSizePx <= 0) return null
    val file = File(path)
    if (!file.isFile) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= targetSizePx ||
        bounds.outHeight / (sampleSize * 2) >= targetSizePx
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    )
}

private const val CONTINUE_REQUEST_CODE_BASE = 10_000
private const val QUOTE_REQUEST_CODE_BASE = 20_000
