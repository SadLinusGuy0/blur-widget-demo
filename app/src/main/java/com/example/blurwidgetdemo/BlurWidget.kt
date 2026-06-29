package com.example.blurwidgetdemo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews

class BlurWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove("level_$id")
            editor.remove("theme_$id")
            editor.remove("tint_hue_$id")
            editor.remove("tint_saturation_$id")
            editor.remove("tint_value_$id")
            editor.remove("tint_alpha_$id")
        }
        editor.apply()
    }

    companion object {
        const val WIDGET_PREFS = "widget_prefs"
        const val LAYOUT_MODE_LARGE = 0
        const val LAYOUT_MODE_COMPACT_STRIP = 1
        const val LAYOUT_MODE_COMPACT_SQUARE = 2
        const val DEFAULT_TINT_HUE = 0f
        const val DEFAULT_TINT_SATURATION = 0f
        const val DEFAULT_TINT_VALUE = 1f
        const val DEFAULT_TINT_ALPHA = 102
        val PRESET_ALPHAS = intArrayOf(38, 102, 178, 240)

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 360)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260)
            val layoutResource = when (layoutMode(minWidth, minHeight)) {
                LAYOUT_MODE_COMPACT_STRIP -> R.layout.widget_compact_strip
                LAYOUT_MODE_COMPACT_SQUARE -> R.layout.widget_compact_square
                else -> R.layout.widget_blur
            }

            val views = RemoteViews(context.packageName, layoutResource).apply {
                setInt(android.R.id.background, "setBackgroundColor", tintColor(prefs, appWidgetId))
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun tintColor(prefs: SharedPreferences, appWidgetId: Int): Int {
            val alpha = if (prefs.contains("tint_alpha_$appWidgetId")) {
                prefs.getInt("tint_alpha_$appWidgetId", DEFAULT_TINT_ALPHA)
            } else {
                val oldLevel = prefs.getInt("level_$appWidgetId", 1).coerceIn(0, PRESET_ALPHAS.lastIndex)
                PRESET_ALPHAS[oldLevel]
            }
            return tintColor(
                prefs.getFloat("tint_hue_$appWidgetId", DEFAULT_TINT_HUE),
                prefs.getFloat("tint_saturation_$appWidgetId", DEFAULT_TINT_SATURATION),
                prefs.getFloat("tint_value_$appWidgetId", DEFAULT_TINT_VALUE),
                alpha
            )
        }

        fun tintColor(hue: Float, saturation: Float, value: Float, alpha: Int): Int =
            Color.HSVToColor(
                alpha.coerceIn(1, 254),
                floatArrayOf(
                    hue.coerceIn(0f, 360f),
                    saturation.coerceIn(0f, 1f),
                    value.coerceIn(0f, 1f)
                )
            )

        fun layoutMode(minWidth: Int, minHeight: Int): Int = when {
            minHeight <= 100 -> LAYOUT_MODE_COMPACT_STRIP
            minWidth <= 220 -> LAYOUT_MODE_COMPACT_SQUARE
            else -> LAYOUT_MODE_LARGE
        }
    }
}
