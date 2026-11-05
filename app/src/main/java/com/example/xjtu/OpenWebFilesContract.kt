package com.example.xjtu

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

internal data class WebFilePickerRequest(
    val acceptTypes: List<String>,
    val allowMultiple: Boolean
)

internal class OpenWebFilesContract : ActivityResultContract<WebFilePickerRequest, Array<Uri>?>() {
    override fun createIntent(context: Context, input: WebFilePickerRequest): Intent {
        val acceptedTypes = normalizeAcceptedMimeTypes(input.acceptTypes)
        val primaryType = acceptedTypes.singleOrNull() ?: "*/*"

        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = primaryType
            if (acceptedTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, acceptedTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, input.allowMultiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Array<Uri>? {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return null

        val selected = linkedSetOf<Uri>()
        intent.clipData?.let { clips ->
            for (index in 0 until clips.itemCount) {
                clips.getItemAt(index).uri?.let(selected::add)
            }
        }
        intent.data?.let(selected::add)
        return selected.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}

internal fun normalizeAcceptedMimeTypes(acceptTypes: List<String>): List<String> {
    return acceptTypes
        .flatMap { it.split(',') }
        .map(String::trim)
        .filter { mimeType ->
            mimeType.count { it == '/' } == 1 &&
                mimeType.all { it.isLetterOrDigit() || it in "!#$&^_.+-*/" } &&
                mimeType.substringBefore('/').isNotEmpty() &&
                mimeType.substringAfter('/').isNotEmpty()
        }
        .map(String::lowercase)
        .distinct()
}
