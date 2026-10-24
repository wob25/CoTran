package com.copytranslate.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.copytranslate.app.R
import com.copytranslate.app.service.ClipboardAccessibilityService
import com.copytranslate.app.service.ClipboardMonitorService
import com.copytranslate.app.service.FloatingButtonService
import com.copytranslate.app.translate.TranslateManager
import com.copytranslate.app.util.Prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 入口页：服务开关状态、悬浮窗权限引导、启动两个前台服务、跳转设置/历史。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvA11yStatus: TextView
    private lateinit var tvDiag: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantA11y: Button
    private lateinit var btnBattery: Button
    private lateinit var btnShowFloat: Button
    private val handler = Handler(Looper.getMainLooper())

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不阻塞服务 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvA11yStatus = findViewById(R.id.tvA11yStatus)
        tvDiag = findViewById(R.id.tvDiag)
        btnToggle = findViewById(R.id.btnToggle)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantA11y = findViewById(R.id.btnGrantA11y)
        btnBattery = findViewById(R.id.btnBattery)
        btnShowFloat = findViewById(R.id.btnShowFloat)

        btnToggle.setOnClickListener { toggleServices() }
        btnGrantOverlay.setOnClickListener { goOverlaySettings() }
        btnGrantA11y.setOnClickListener { goAccessibilitySettings() }
        btnBattery.setOnClickListener { requestIgnoreBatteryOptimization() }
        btnShowFloat.setOnClickListener { reshowFloatingButton() }
        // 界面语言切换（仅影响应用内文字，翻译功能不变）；按钮只显示当前生效语言的国旗
        val btnLang = findViewById<TextView>(R.id.btnLang)
        val (curFlag, _) = com.copytranslate.app.util.AppLanguage.displayFor(
            com.copytranslate.app.util.AppLanguage.currentTag())
        btnLang.text = curFlag
        btnLang.setOnClickListener { showLanguageDialog() }
        // 离线模型预热：优先播种 APK 内置模型，缺失时静默联网下载（失败不影响使用）
        com.copytranslate.app.translate.provider.MlKitModelWarmer.warmAsync(applicationContext)
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener { showManualInputDialog() }
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refreshUi() {
        val running = ClipboardMonitorService.running
        tvStatus.text = getString(if (running) R.string.main_status_on else R.string.main_status_off)
        btnToggle.setText(if (running) R.string.btn_toggle_stop else R.string.btn_toggle_start)
        btnGrantOverlay.visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE

        // 无障碍服务状态（Android 10+ 后台读剪贴板的唯一通道）
        val a11yOn = isAccessibilityEnabled()
        btnGrantA11y.visibility = if (a11yOn) View.GONE else View.VISIBLE
        tvA11yStatus.text = getString(
            if (a11yOn) R.string.a11y_status_on else R.string.a11y_status_off
        )
        // 红绿高亮，状态一目了然
        tvA11yStatus.setTextColor(
            if (a11yOn) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt()
        )

        // 电池优化豁免引导
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        btnBattery.visibility =
            if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE

        // 悬浮按钮被用户隐藏后，提供重新显示入口
        btnShowFloat.visibility =
            if (ClipboardMonitorService.running && Prefs.floatingHidden) View.VISIBLE else View.GONE

        // [自检] 显示最近一次后台成功读取剪贴板的时间与内容
        val detectedAt = Prefs.lastDetectedAt
        if (detectedAt == 0L) {
            tvDiag.text = getString(R.string.diag_not_yet)
        } else {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(detectedAt))
            tvDiag.text = getString(R.string.diag_detected, time, Prefs.lastDetectedPreview)
        }
    }

    /** 判断本应用的无障碍服务是否已在系统中开启 */
    private fun isAccessibilityEnabled(): Boolean {
        if (ClipboardAccessibilityService.running) return true
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo?.serviceInfo?.packageName == packageName }
    }

    private fun goAccessibilitySettings() {
        Toast.makeText(this, R.string.a11y_guide_toast, Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** 引导关闭电池优化，防止前台服务被 vivo 等系统清理 */
    private fun requestIgnoreBatteryOptimization() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    /** 重新显示被隐藏的悬浮按钮 */
    private fun reshowFloatingButton() {
        Prefs.floatingHidden = false
        FloatingButtonService.showButton(this)
        refreshUi()
        Toast.makeText(this, R.string.toast_float_reshown, Toast.LENGTH_SHORT).show()
    }

    private fun toggleServices() {
        if (ClipboardMonitorService.running) {
            stopService(Intent(this, ClipboardMonitorService::class.java))
            stopService(Intent(this, FloatingButtonService::class.java))
            handler.postDelayed({ refreshUi() }, 300)
            refreshUi()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            goOverlaySettings()
            return
        }
        // Android 10+ 必须开启无障碍，否则后台检测不到复制，服务开了也是空转
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isAccessibilityEnabled()) {
            Toast.makeText(this, R.string.a11y_required_toast, Toast.LENGTH_LONG).show()
            goAccessibilitySettings()
            return
        }
        requestNotificationPermissionIfNeeded()
        ContextCompat.startForegroundService(
            this, Intent(this, ClipboardMonitorService::class.java)
        )
        ContextCompat.startForegroundService(
            this, Intent(this, FloatingButtonService::class.java)
        )
        // 服务生命周期异步，稍后刷新状态显示
        handler.postDelayed({ refreshUi() }, 300)
        refreshUi()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun goOverlaySettings() {
        Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    /**
     * 手动输入翻译（两段式）：输入弹窗（左上小标题 + 右上清除清空输入）→ 翻译 → 结果弹窗（中间显示平台名）。
     */
    private fun showManualInputDialog() {
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_manual_input, null)
        val input = view.findViewById<EditText>(R.id.etManualInput)
        // 前台状态下剪贴板可读，自动预填当前剪贴板内容
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!clip.isNullOrBlank()) input.setText(clip)

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(R.string.overlay_btn_translate) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) translateManualText(text)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
        // 清除：清空输入框全部内容
        view.findViewById<TextView>(R.id.tvManualClear).setOnClickListener {
            input.setText("")
        }
    }

    /** 在主界面内翻译手动输入的文本并弹出结果弹窗 */
    private fun translateManualText(text: String) {
        val progress = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manual_title)
            .setMessage(R.string.overlay_translating)
            .setCancelable(false)
            .show()
        CoroutineScope(Dispatchers.Main).launch {
            val result = TranslateManager.translate(text)
            progress.dismiss()
            result.fold(
                onSuccess = { r ->
                    com.copytranslate.app.util.ClipboardGate.markShown(r.translatedText)
                    showManualResult(r)
                },
                onFailure = { e ->
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.translate_failed_title)
                        .setMessage(getString(R.string.translate_failed, e.message ?: ""))
                        .setPositiveButton(R.string.btn_cancel, null)
                        .show()
                }
            )
        }
    }

    /** 结果弹窗：左上小标题(译文) + 中间平台名 + 右上清除（清空译文）+ 可复制 */
    private fun showManualResult(r: com.copytranslate.app.translate.TranslateResult) {
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_manual_result, null)
        val tv = view.findViewById<TextView>(R.id.tvResultText)
        tv.text = r.translatedText
        view.findViewById<TextView>(R.id.tvResultPlatform).text =
            if (r.provider.isNotBlank()) getString(R.string.manual_via, providerDisplayName(r.provider)) else ""
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(R.string.overlay_btn_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("translation", r.translatedText))
                Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** 界面语言选择弹窗：跟随系统 + 68 种语言（国旗 + 原生名称），选择后应用自动以新语言重建 */
    private fun showLanguageDialog() {
        val current = com.copytranslate.app.util.AppLanguage.currentTag()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(pad, pad / 2, pad, pad / 2)

        fun addLangRow(flag: String, name: String, tag: String?) {
            val selected = (current == null && tag == null) || current == tag
            val row = TextView(this).apply {
                text = "$flag  $name${if (selected) "  ✓" else ""}"
                textSize = 16f
                setTextColor(getColor(if (selected) R.color.color_primary else R.color.text_primary))
                val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = ta.getDrawable(0)
                ta.recycle()
                setPadding(pad / 2, pad / 3, pad / 2, pad / 3)
                setOnClickListener {
                    com.copytranslate.app.util.AppLanguage.apply(tag)
                    // 服务层 Context 在 attachBaseContext 时定死语言，切换后重启服务让悬浮弹窗/通知立即换语言
                    if (com.copytranslate.app.service.ClipboardMonitorService.running) {
                        this@MainActivity.stopService(Intent(this@MainActivity, FloatingButtonService::class.java))
                        ContextCompat.startForegroundService(
                            this@MainActivity, Intent(this@MainActivity, FloatingButtonService::class.java)
                        )
                    }
                }
            }
            container.addView(row)
        }

        // 首行：跟随系统语言（显示系统语言的国旗+名称）
        val (sysFlag, sysName) = com.copytranslate.app.util.AppLanguage.displayFor(null)
        addLangRow(sysFlag, getString(R.string.lang_follow_system) + "（$sysName）", null)
        // 68 种可选语言
        com.copytranslate.app.util.AppLanguage.LANGS.forEach { (code, flag, native) ->
            addLangRow(flag, native, code)
        }

        val scroll = ScrollView(this).apply { addView(container) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lang_choose_title)
            .setView(scroll)
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** 内部平台名 → 中文显示名（与悬浮面板一致） */
    private fun providerDisplayName(id: String): String = id.split("+").joinToString("+") { p ->
        when (p) {
            "MLKit" -> "离线"
            "BiliIndex" -> "B站"
            "Google" -> "谷歌"
            "GoogleCloud" -> "谷歌云"
            "zvo.cn" -> "zvo.cn"
            "MyMemory" -> "MyMemory"
            "Microsoft" -> "微软"
            "Baidu" -> "百度"
            "DeepL" -> "DeepL"
            else -> p
        }
    }
}
