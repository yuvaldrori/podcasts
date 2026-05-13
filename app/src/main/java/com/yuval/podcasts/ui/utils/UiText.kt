package com.yuval.podcasts.ui.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A sealed class to represent text that can be either a raw string or a string resource.
 * This allows ViewModels to provide translatable text without needing a [Context] instance.
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    class StringResource(
        @param:StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> {
                if (args.isEmpty()) stringResource(resId)
                else stringResource(resId, *args)
            }
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> {
                if (args.isEmpty()) context.getString(resId)
                else context.getString(resId, *args)
            }
        }
    }
}
