package com.example.blurwidgetdemo

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.picker3.app.SeslColorPickerDialog
import dev.oneuiproject.oneui.widget.CardItemView
import dev.oneuiproject.oneui.widget.SwitchItemView
import kotlin.math.min
import kotlin.math.roundToInt

private fun Drawable.copyForPreview(): Drawable =
    constantState?.newDrawable()?.mutate() ?: mutate()

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var currentHue = BlurWidget.DEFAULT_TINT_HUE
    private var currentSaturation = BlurWidget.DEFAULT_TINT_SATURATION
    private var currentValue = BlurWidget.DEFAULT_TINT_VALUE
    private var currentAlpha = BlurWidget.DEFAULT_TINT_ALPHA
    private var useDarkTint = false
    private var suppressTintModeChange = false
    private var recentColors = listOf(BlurWidget.tintColor(
        BlurWidget.DEFAULT_TINT_HUE,
        BlurWidget.DEFAULT_TINT_SATURATION,
        BlurWidget.DEFAULT_TINT_VALUE,
        BlurWidget.DEFAULT_TINT_ALPHA
    ))
    private var colorPickerDialog: SeslColorPickerDialog? = null
    private var previewWallpaper: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_widget_config)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        loadSettings()
        setupWallpaperPreview()
        setupColourPicker()
        setupPresetControls()
        setupButtons()
        updatePreview()
    }

    override fun onDestroy() {
        colorPickerDialog?.dismiss()
        colorPickerDialog = null
        super.onDestroy()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(BlurWidget.WIDGET_PREFS, MODE_PRIVATE)
        currentHue = prefs.getFloat("tint_hue_$appWidgetId", BlurWidget.DEFAULT_TINT_HUE)
        currentSaturation = prefs.getFloat("tint_saturation_$appWidgetId", BlurWidget.DEFAULT_TINT_SATURATION)
        currentValue = prefs.getFloat("tint_value_$appWidgetId", BlurWidget.DEFAULT_TINT_VALUE)
        currentAlpha = if (prefs.contains("tint_alpha_$appWidgetId")) {
            prefs.getInt("tint_alpha_$appWidgetId", BlurWidget.DEFAULT_TINT_ALPHA)
        } else {
            val oldLevel = prefs.getInt("level_$appWidgetId", 1).coerceIn(0, BlurWidget.PRESET_ALPHAS.lastIndex)
            BlurWidget.PRESET_ALPHAS[oldLevel]
        }.coerceIn(MIN_BLUR_ALPHA, MAX_BLUR_ALPHA)
        useDarkTint = currentSaturation < NEUTRAL_SATURATION_THRESHOLD && currentValue < DARK_VALUE_THRESHOLD
        recentColors = listOf(currentTintColor(), BlurWidget.tintColor(
            BlurWidget.DEFAULT_TINT_HUE,
            BlurWidget.DEFAULT_TINT_SATURATION,
            BlurWidget.DEFAULT_TINT_VALUE,
            BlurWidget.DEFAULT_TINT_ALPHA
        )).distinct().take(MAX_RECENT_COLORS)
    }

    private fun setupWallpaperPreview() {
        val wallpaper = wallpaperPreviewDrawable()
        previewWallpaper = wallpaper
        findViewById<ImageView>(R.id.preview_wallpaper).setImageDrawable(wallpaper.copyForPreview())
    }

    private fun setupColourPicker() {
        findViewById<View>(R.id.color_picker_row).setOnClickListener {
            openColorPickerDialog()
        }
        updateColourUi()
    }

    private fun openColorPickerDialog() {
        colorPickerDialog?.dismiss()
        colorPickerDialog = SeslColorPickerDialog(
            this,
            { color -> onColorPicked(color) },
            currentTintColor(),
            recentColors.toIntArray(),
            true
        ).apply {
            setTransparencyControlEnabled(true)
            show()
            window?.decorView?.post {
                setOnBitmapSetListener { captureScreenBitmap() }
            }
        }
    }

    private fun onColorPicked(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0]
        currentSaturation = hsv[1]
        currentValue = hsv[2]
        currentAlpha = Color.alpha(color).coerceIn(MIN_BLUR_ALPHA, MAX_BLUR_ALPHA)
        useDarkTint = Color.luminance(color) < DARK_LUMINANCE_THRESHOLD
        recentColors = (listOf(currentTintColor()) + recentColors).distinct().take(MAX_RECENT_COLORS)
        updateTintModeSwitch()
        updateColourUi()
        updatePreview()
    }

    private fun captureScreenBitmap(): Bitmap {
        val rootView = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width.coerceAtLeast(1), rootView.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        rootView.draw(Canvas(bitmap))
        return bitmap
    }

    private fun setupPresetControls() {
        findViewById<SwitchItemView>(R.id.dark_tint_switch).apply {
            isChecked = useDarkTint
            onCheckedChangedListener = { _, checked ->
                if (!suppressTintModeChange) {
                    useDarkTint = checked
                    applyNeutralTint(currentAlpha)
                }
            }
        }

        OPACITY_PRESETS.forEach { preset ->
            findViewById<TextView>(preset.viewId).setOnClickListener {
                applyNeutralTint(preset.alpha)
            }
        }
        findViewById<TextView>(R.id.preset_default).setOnClickListener {
            resetToDefault()
        }
    }

    private fun applyNeutralTint(alpha: Int) {
        currentAlpha = alpha.coerceIn(MIN_BLUR_ALPHA, MAX_BLUR_ALPHA)
        currentHue = BlurWidget.DEFAULT_TINT_HUE
        currentSaturation = BlurWidget.DEFAULT_TINT_SATURATION
        currentValue = if (useDarkTint) 0f else 1f
        recentColors = (listOf(currentTintColor()) + recentColors).distinct().take(MAX_RECENT_COLORS)
        updateTintModeSwitch()
        updateColourUi()
        updatePreview()
    }

    private fun resetToDefault() {
        useDarkTint = false
        currentHue = BlurWidget.DEFAULT_TINT_HUE
        currentSaturation = BlurWidget.DEFAULT_TINT_SATURATION
        currentValue = BlurWidget.DEFAULT_TINT_VALUE
        currentAlpha = BlurWidget.DEFAULT_TINT_ALPHA
        updateTintModeSwitch()
        updateColourUi()
        updatePreview()
    }

    private fun updateTintModeSwitch() {
        suppressTintModeChange = true
        findViewById<SwitchItemView>(R.id.dark_tint_switch).isChecked = useDarkTint
        suppressTintModeChange = false
    }

    private fun updateColourUi() {
        val color = currentTintColor()
        val tintRow = findViewById<CardItemView>(R.id.color_picker_row)
        tintRow.summary = getString(
            R.string.tint_values,
            argbHex(color),
            opacityPercent(currentAlpha)
        )
        tintRow.getEndImageView().apply {
            setImageDrawable(null)
            background = roundedSwatchDrawable(color)
        }
    }

    private fun setupButtons() {
        findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        findViewById<TextView>(R.id.btn_save).setOnClickListener {
            saveAndFinish()
        }
    }

    private data class PreviewSpec(val minWidthDp: Int, val minHeightDp: Int, val layoutMode: Int)
    private data class OpacityPreset(val viewId: Int, val alpha: Int)

    private fun updatePreview() {
        val spec = currentPreviewSpec()
        val previewWidget = findViewById<FrameLayout>(R.id.preview_widget)

        sizePreviewWidget(previewWidget, spec)
        renderPreviewWidget(previewWidget, spec)
    }

    private fun currentPreviewSpec(): PreviewSpec {
        val options = runCatching {
            AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId)
        }.getOrNull()
        val minWidth = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            ?.takeIf { it > 0 }
            ?: 360
        val minHeight = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: 260
        return PreviewSpec(minWidth, minHeight, BlurWidget.layoutMode(minWidth, minHeight))
    }

    private fun sizePreviewWidget(previewWidget: FrameLayout, spec: PreviewSpec) {
        val width = spec.minWidthDp.coerceAtLeast(150)
        val height = spec.minHeightDp.coerceAtLeast(64)
        previewWidget.layoutParams = FrameLayout.LayoutParams(dp(width), dp(height), Gravity.CENTER)
        previewWidget.post {
            val container = findViewById<FrameLayout>(R.id.preview_container)
            val availableWidth = (container.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - dp(34)
            val availableHeight = (container.height.takeIf { it > 0 } ?: dp(242)) - dp(28)
            val scale = min(
                availableWidth.toFloat() / previewWidget.width.coerceAtLeast(1),
                availableHeight.toFloat() / previewWidget.height.coerceAtLeast(1)
            ).coerceAtMost(1f)
            previewWidget.pivotX = previewWidget.width / 2f
            previewWidget.pivotY = previewWidget.height / 2f
            previewWidget.scaleX = scale
            previewWidget.scaleY = scale
        }
    }

    private fun renderPreviewWidget(previewWidget: FrameLayout, spec: PreviewSpec) {
        previewWidget.removeAllViews()
        previewWidget.background = roundedDrawable(Color.TRANSPARENT, previewRadiusFor(spec))
        previewWidget.clipToOutline = true

        val wallpaperLayer = ImageView(this).apply {
            setImageDrawable(previewWallpaper?.copyForPreview() ?: wallpaperPreviewDrawable().also { previewWallpaper = it }.copyForPreview())
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.9f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP))
            }
        }
        previewWidget.addView(wallpaperLayer, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        previewWidget.addView(
            View(this).apply { background = roundedDrawable(currentTintColor(), previewRadiusFor(spec)) },
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun currentTintColor(): Int =
        BlurWidget.tintColor(currentHue, currentSaturation, currentValue, currentAlpha)

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(color)
        }

    private fun roundedSwatchDrawable(color: Int): GradientDrawable =
        roundedDrawable(color, 16f).apply {
            setStroke(dp(1), Color.argb(70, 0, 0, 0))
        }

    private fun previewRadiusFor(spec: PreviewSpec): Float = when (spec.layoutMode) {
        BlurWidget.LAYOUT_MODE_COMPACT_STRIP -> 38f
        else -> 24f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun saveAndFinish() {
        getSharedPreferences(BlurWidget.WIDGET_PREFS, MODE_PRIVATE).edit()
            .putFloat("tint_hue_$appWidgetId", currentHue)
            .putFloat("tint_saturation_$appWidgetId", currentSaturation)
            .putFloat("tint_value_$appWidgetId", currentValue)
            .putInt("tint_alpha_$appWidgetId", currentAlpha)
            .apply()

        BlurWidget.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    @SuppressLint("MissingPermission")
    private fun wallpaperPreviewDrawable(): Drawable {
        val manager = WallpaperManager.getInstance(this)
        listOf<() -> Drawable?>(
            { manager.samsungSemDrawable(SAMSUNG_SUB_HOME) },
            { manager.samsungSemDrawable(SAMSUNG_MAIN_HOME) },
            { manager.samsungSemDrawable(SAMSUNG_SYSTEM_HOME) },
            { manager.samsungAppliedWallpaperDrawable() },
            { manager.appliedSystemWallpaperDrawable() },
            { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) manager.getDrawable(WallpaperManager.FLAG_SYSTEM) else null },
            { manager.peekDrawable() },
            { manager.drawable },
            { manager.fastDrawable },
            { manager.builtInDrawable }
        ).forEach { loader ->
            try {
                loader()?.let { return it }
            } catch (_: Exception) {
                // Try the next wallpaper API before falling back to a representative home-screen wash.
            }
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(116, 142, 158),
                Color.rgb(196, 164, 186),
                Color.rgb(226, 232, 232)
            )
        ).apply {
            cornerRadius = 28f * resources.displayMetrics.density
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun WallpaperManager.samsungSemDrawable(which: Int): Drawable? =
        runCatching {
            val method = javaClass.methods.firstOrNull { method ->
                method.name == "semGetDrawable" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Integer.TYPE
            } ?: javaClass.declaredMethods.firstOrNull { method ->
                method.name == "semGetDrawable" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Integer.TYPE
            } ?: return null
            method.isAccessible = true
            method.invoke(this, which) as? Drawable
        }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun WallpaperManager.appliedSystemWallpaperDrawable(): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)?.let { BitmapDrawable(resources, it) }
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun WallpaperManager.samsungAppliedWallpaperDrawable(): Drawable? {
        val methods = (javaClass.methods.asSequence() + javaClass.declaredMethods.asSequence())
            .distinctBy { "${it.name}:${it.parameterTypes.joinToString()}" }
            .filter { method ->
                method.name.contains("wallpaper", ignoreCase = true) ||
                    method.name.contains("sem", ignoreCase = true)
            }
            .filter { method -> method.parameterTypes.all { it == Integer.TYPE } }
            .toList()

        val whichCandidates = intArrayOf(SAMSUNG_SUB_HOME, SAMSUNG_MAIN_HOME, SAMSUNG_SYSTEM_HOME, WallpaperManager.FLAG_SYSTEM)
        val userCandidates = intArrayOf(0)
        methods.forEach { method ->
            val args = when (method.parameterTypes.size) {
                1 -> whichCandidates.map { arrayOf<Any>(it) }
                2 -> whichCandidates.flatMap { which ->
                    userCandidates.flatMap { user ->
                        listOf(arrayOf<Any>(which, user), arrayOf<Any>(user, which))
                    }
                }
                else -> emptyList()
            }
            args.forEach { candidate ->
                runCatching {
                    method.isAccessible = true
                    when (val result = method.invoke(this, *candidate)) {
                        is Drawable -> return result
                        is Bitmap -> return BitmapDrawable(resources, result)
                        is ParcelFileDescriptor -> result.use { descriptor ->
                            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)?.let {
                                return BitmapDrawable(resources, it)
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun argbHex(color: Int): String = "#${color.toUInt().toString(16).uppercase().padStart(8, '0')}"
    private fun opacityPercent(alpha: Int): Int = (alpha.coerceIn(0, 255) / 255f * 100f).roundToInt()

    companion object {
        private const val MIN_BLUR_ALPHA = 1
        private const val MAX_BLUR_ALPHA = 254
        private const val MAX_RECENT_COLORS = 6
        private const val NEUTRAL_SATURATION_THRESHOLD = 0.05f
        private const val DARK_VALUE_THRESHOLD = 0.5f
        private const val DARK_LUMINANCE_THRESHOLD = 0.5f
        private const val SAMSUNG_MAIN_HOME = 5
        private const val SAMSUNG_SUB_HOME = 17
        private const val SAMSUNG_SYSTEM_HOME = 1
        private val OPACITY_PRESETS = listOf(
            OpacityPreset(R.id.preset_glass, 38),
            OpacityPreset(R.id.preset_light, 102),
            OpacityPreset(R.id.preset_medium, 178),
            OpacityPreset(R.id.preset_solid, 240)
        )
    }
}
