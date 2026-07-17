package com.aritiq.calcnote.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

actual fun shareExport(context: Any, content: String, mimeType: String, filename: String) {
    val ctx = context as Context
    val file = File(ctx.cacheDir, filename)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share"))
}
