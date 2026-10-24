package com.copytranslate.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.copytranslate.app.R
import com.copytranslate.app.util.Prefs
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 设置页：翻译源、目标语言、悬浮按钮自动隐藏秒数、高级密钥配置。
 * 所有改动即时写入 Prefs，立即生效。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setTitle(R.string.settings_title)

        setupProvider()
        setupTargetLang()
        setupButtonSize()
        setupButtonOpacity()
        setupSwitches()
        setupAdvanced()
    }

    private fun setupButtonSize() {
        val slider = findViewById<com.google.android.material.slider.Slider>(R.id.sliderBtnSize)
        val tvValue = findViewById<TextView>(R.id.tvBtnSizeValue)
        slider.value = Prefs.buttonSizeDp.toFloat()
        tvValue.text = getString(R.string.btnsize_current, Prefs.buttonSizeDp)
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val dp = value.toInt()
                Prefs.buttonSizeDp = dp
                tvValue.text = getString(R.string.btnsize_current, dp)
                // 即时生效：让悬浮按钮服务按新尺寸重建按钮
                com.copytranslate.app.service.FloatingButtonService.refreshButton()
            }
        }
    }

    /** 悬浮按钮背景透明度：10-100 拖动即时生效 */
    private fun setupButtonOpacity() {
        val slider = findViewById<com.google.android.material.slider.Slider>(R.id.sliderBtnOpacity)
        val tvValue = findViewById<TextView>(R.id.tvBtnOpacityValue)
        slider.value = Prefs.floatingOpacity.toFloat()
        tvValue.text = getString(R.string.btnopacity_current, Prefs.floatingOpacity)
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val pct = value.toInt()
                Prefs.floatingOpacity = pct
                tvValue.text = getString(R.string.btnopacity_current, pct)
                com.copytranslate.app.service.FloatingButtonService.refreshButton()
            }
        }
    }

    private fun setupSwitches() {
        val cbBoot = findViewById<CheckBox>(R.id.cbAutoBoot)
        cbBoot.isChecked = Prefs.autoStartOnBoot
        cbBoot.setOnCheckedChangeListener { _, c -> Prefs.autoStartOnBoot = c }

        val cbLog = findViewById<CheckBox>(R.id.cbDebugLog)
        cbLog.isChecked = Prefs.debugLog
        cbLog.setOnCheckedChangeListener { _, c -> Prefs.debugLog = c }
    }

    private fun setupProvider() {
        val rg = findViewById<RadioGroup>(R.id.rgProvider)
        val checkedId = when (Prefs.provider) {
            Prefs.PROVIDER_BILI -> R.id.rbProviderBili
            Prefs.PROVIDER_GOOGLE -> R.id.rbProviderGoogle
            Prefs.PROVIDER_ZVO -> R.id.rbProviderZvo
            Prefs.PROVIDER_MYMEMORY -> R.id.rbProviderMymemory
            Prefs.PROVIDER_MLKIT -> R.id.rbProviderMlkit
            Prefs.PROVIDER_MICROSOFT -> R.id.rbProviderMicrosoft
            Prefs.PROVIDER_GCLOUD -> R.id.rbProviderGcloud
            Prefs.PROVIDER_BAIDU -> R.id.rbProviderBaidu
            Prefs.PROVIDER_DEEPL -> R.id.rbProviderDeepl
            else -> R.id.rbProviderAuto
        }
        // 先选中再挂监听，避免初始化触发写入
        rg.check(checkedId)
        rg.setOnCheckedChangeListener { _, id ->
            val newProvider = when (id) {
                R.id.rbProviderBili -> Prefs.PROVIDER_BILI
                R.id.rbProviderGoogle -> Prefs.PROVIDER_GOOGLE
                R.id.rbProviderZvo -> Prefs.PROVIDER_ZVO
                R.id.rbProviderMymemory -> Prefs.PROVIDER_MYMEMORY
                R.id.rbProviderMicrosoft -> Prefs.PROVIDER_MICROSOFT
                R.id.rbProviderGcloud -> Prefs.PROVIDER_GCLOUD
                R.id.rbProviderBaidu -> Prefs.PROVIDER_BAIDU
                R.id.rbProviderDeepl -> Prefs.PROVIDER_DEEPL
                else -> Prefs.PROVIDER_AUTO
            }
            Prefs.provider = newProvider
        }
        // 点击「离线翻译」无论是否已选中都弹出语言包管理弹窗
        // （RadioGroup 监听只在选中项变化时触发，重复点击已选项不会再弹，故单独挂点击监听）
        findViewById<android.widget.RadioButton>(R.id.rbProviderMlkit).setOnClickListener {
            Prefs.provider = Prefs.PROVIDER_MLKIT
            showModelManagerDialog()
        }
    }

    /** 离线语言包管理弹窗的一行（单选框 + 语言名 + 卸载 + 状态/进度 + 下载按钮） */
    private class PkgRow(
        val code: String,
        val radio: android.widget.RadioButton,
        val status: TextView,
        val progress: ProgressBar,
        val btn: TextView,
        val delBtn: TextView
    )

    /** 可离线下载的语言包（ML Kit 支持的全部语言，含内置中英包的 zh；标签用各语言原生名称，与语言弹窗一致） */
    private val offlinePkgs: List<Pair<String, String>> =
        com.copytranslate.app.util.AppLanguage.LANGS.map { it.first to it.third }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * 离线语言包管理弹窗：
     * 已下载显示「已安装」；未下载显示「下载」按钮；下载中显示进度条与已下载体积。
     */
    private fun showModelManagerDialog() {
        val pad = dp(20)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, dp(4))
        }
        container.addView(TextView(this).apply {
            setText(R.string.model_mgr_hint)
            textSize = 12f
        })

        val rows = mutableListOf<PkgRow>()

        // 顶部「自动」选项：默认方式（检测源语言 + 按目标语言设置选包）；手动选包后优选手动选择
        val autoRadio = android.widget.RadioButton(this).apply {
            text = getString(R.string.lang_auto)
            textSize = 15f
            isChecked = Prefs.targetLang == Prefs.LANG_AUTO
        }
        autoRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                rows.forEach { it.radio.isChecked = false }
                Prefs.targetLang = Prefs.LANG_AUTO
            }
        }
        val autoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(autoRadio)
        }
        container.addView(autoRow, 1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        offlinePkgs.forEach { (code, label) ->
            // 圆形单选框：已安装才可选（选中=作为离线翻译的目标语言）；未安装置灰
            val radio = android.widget.RadioButton(this).apply {
                isChecked = Prefs.provider == Prefs.PROVIDER_MLKIT && Prefs.targetLang == code
                isEnabled = false // 初始置灰，状态刷新后按已安装与否启用
                alpha = 0.4f
            }
            val status = TextView(this).apply {
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(88)
            }
            // 卸载按钮：已安装时显示，点击删除该离线包
            val delBtn = TextView(this).apply {
                text = getString(R.string.model_pkg_delete)
                textSize = 14f
                setTextColor(getColor(R.color.error_red))
                val ta2 = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                background = ta2.getDrawable(0)
                ta2.recycle()
                setPadding(dp(10), dp(4), dp(10), dp(4))
                visibility = View.GONE
            }
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT)
                max = 100
                visibility = View.GONE
            }
            val btn = TextView(this).apply {
                text = getString(R.string.model_pkg_download)
                textSize = 14f
                setTextColor(getColor(R.color.color_primary))
                val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                background = ta.getDrawable(0)
                ta.recycle()
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }
            val name = TextView(this).apply {
                text = label
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(radio)
                addView(name)
                addView(progress)
                addView(delBtn)
                addView(status)
                addView(btn)
            }
            container.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })

            val pkgRow = PkgRow(code, radio, status, progress, btn, delBtn)
            // 单选互斥：选中语言包 = 优先用该包翻译（作为目标语言），并取消「自动」与其他行
            radio.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    autoRadio.isChecked = false
                    rows.forEach { if (it !== pkgRow) it.radio.isChecked = false }
                    Prefs.targetLang = code
                }
            }
            btn.setOnClickListener { downloadPkg(pkgRow) }
            delBtn.setOnClickListener { deletePkg(pkgRow) }
            rows.add(pkgRow)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.model_mgr_title)
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(R.string.model_close, null)
            .show()

        // 异步刷新各语言包的已安装状态
        lifecycleScope.launch { rows.forEach { refreshPkgStatus(it) } }
    }

    /** 查询语言包是否已下载（ML Kit 每个语言一个包，双向共用） */
    private suspend fun refreshPkgStatus(row: PkgRow): Boolean {
        val installed = runCatching {
            RemoteModelManager.getInstance()
                .isModelDownloaded(TranslateRemoteModel.Builder(row.code).build()).awaitTask()
        }.getOrDefault(false)
        if (installed) {
            row.status.text = getString(R.string.model_pkg_installed)
            row.status.setTextColor(getColor(R.color.text_secondary))
            row.btn.visibility = View.GONE
            row.delBtn.visibility = View.VISIBLE
            row.radio.isEnabled = true
            row.radio.alpha = 1f
        } else {
            row.status.text = ""
            row.btn.visibility = View.VISIBLE
            row.delBtn.visibility = View.GONE
            row.radio.isEnabled = false
            row.radio.alpha = 0.4f
        }
        return installed
    }

    /** 卸载离线语言包：删除已下载模型；若该包是当前选中目标则恢复「自动」 */
    private fun deletePkg(row: PkgRow) {
        row.delBtn.visibility = View.GONE
        row.status.text = getString(R.string.model_pkg_downloading)
        row.status.setTextColor(getColor(R.color.text_secondary))
        lifecycleScope.launch {
            val ok = runCatching {
                RemoteModelManager.getInstance().deleteDownloadedModel(
                    TranslateRemoteModel.Builder(row.code).build()
                ).awaitTask()
            }.isSuccess
            withContext(Dispatchers.Main) {
                if (ok) {
                    row.status.text = ""
                    row.radio.isChecked = false
                    row.radio.isEnabled = false
                    row.radio.alpha = 0.4f
                    if (Prefs.targetLang == row.code) Prefs.targetLang = Prefs.LANG_AUTO
                    row.btn.visibility = View.VISIBLE
                    android.widget.Toast.makeText(
                        this@SettingsActivity, R.string.model_pkg_deleted, android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    row.status.text = getString(R.string.model_pkg_failed)
                    row.status.setTextColor(getColor(R.color.error_red))
                }
                row.delBtn.visibility = if (ok) View.GONE else View.VISIBLE
            }
        }
    }

    /** 下载语言包：ML Kit 下载无进度回调，且文件先落在临时目录、完成才搬进模型目录，
     *  因此监控整个应用数据区（no_backup + files + cache）的体积增量来实时驱动进度条（按 ~45MB 估算） */
    private fun downloadPkg(row: PkgRow) {
        row.btn.visibility = View.GONE
        row.delBtn.visibility = View.GONE
        row.progress.visibility = View.VISIBLE
        row.progress.progress = 0
        row.status.text = getString(R.string.model_pkg_downloading)
        row.status.setTextColor(getColor(R.color.text_secondary))
        lifecycleScope.launch {
            val polling = launch(Dispatchers.IO) {
                fun watchedSize(): Long = listOf(
                    applicationContext.noBackupFilesDir,
                    applicationContext.filesDir,
                    applicationContext.cacheDir
                ).sumOf { root ->
                    root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                }
                // 以点击下载瞬间的体积为基线，增量即本次下载量
                val baseline = runCatching { watchedSize() }.getOrDefault(0L)
                var downloaded = 0L
                while (isActive) {
                    val total = runCatching { watchedSize() }.getOrDefault(baseline)
                    val delta = (total - baseline).coerceAtLeast(0L)
                    // 临时文件被清理导致体积回落的场合，保留已达到的最大值
                    if (delta > downloaded) downloaded = delta
                    val mb = downloaded / (1024.0 * 1024)
                    val pct = ((mb / 45.0) * 100).toInt().coerceIn(0, 95)
                    withContext(Dispatchers.Main) {
                        row.progress.progress = pct
                        row.status.text = String.format(Locale.getDefault(), "下载中… %.1fMB / 约45MB", mb)
                    }
                    delay(300)
                }
            }
            val res = runCatching {
                // 一个语言一个包（含双向），一次下载即可
                RemoteModelManager.getInstance().download(
                    TranslateRemoteModel.Builder(row.code).build(),
                    com.google.mlkit.common.model.DownloadConditions.Builder().build()
                ).awaitTask()
            }
            polling.cancel()
            withContext(Dispatchers.Main) {
                if (res.isSuccess) {
                    row.progress.visibility = View.GONE
                    row.status.text = getString(R.string.model_pkg_done)
                    row.radio.isEnabled = true
                    row.radio.alpha = 1f
                    row.delBtn.visibility = View.VISIBLE
                } else {
                    row.progress.visibility = View.GONE
                    // 显示具体失败原因（网络/密钥/服务不可达等），便于排查
                    val why = res.exceptionOrNull()?.message?.trim()?.take(60).orEmpty()
                    row.status.text = if (why.isEmpty()) getString(R.string.model_pkg_failed)
                    else "下载失败：$why"
                    row.status.setTextColor(getColor(R.color.error_red))
                    row.btn.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    /** 目标语言列表：自动 + 中文 + 全部离线语言（与语言包管理一致），代码动态生成单选项 */
    private fun setupTargetLang() {
        val rg = findViewById<RadioGroup>(R.id.rgTarget)
        // 自动 + 全部离线语言（zh 已包含在 offlinePkgs 中，与语言包管理一致）
        val targetLangs = listOf(Prefs.LANG_AUTO to getString(R.string.target_auto)) + offlinePkgs
        rg.removeAllViews()
        targetLangs.forEach { (code, label) ->
            val rb = android.widget.RadioButton(this).apply {
                text = label
                id = View.generateViewId()
                minHeight = dp(40)
                isChecked = Prefs.targetLang == code
                setOnCheckedChangeListener { _, checked ->
                    if (checked) Prefs.targetLang = code
                }
            }
            rg.addView(rb)
        }
    }

    private fun setupAdvanced() {
        val llAdvanced = findViewById<LinearLayout>(R.id.llAdvanced)
        val tvToggle = findViewById<TextView>(R.id.tvAdvancedToggle)
        tvToggle.setOnClickListener {
            val show = llAdvanced.visibility == View.GONE
            llAdvanced.visibility = if (show) View.VISIBLE else View.GONE
            tvToggle.setText(if (show) R.string.settings_advanced_opened else R.string.settings_advanced_closed)
        }
        bindEdit(R.id.etMicrosoftKey, Prefs.microsoftKey) { Prefs.microsoftKey = it }
        bindEdit(R.id.etMicrosoftRegion, Prefs.microsoftRegion) { Prefs.microsoftRegion = it }
        bindEdit(R.id.etGcloudKey, Prefs.googleCloudKey) { Prefs.googleCloudKey = it }
        bindEdit(R.id.etBaiduAppId, Prefs.baiduAppId) { Prefs.baiduAppId = it }
        bindEdit(R.id.etBaiduSecret, Prefs.baiduSecret) { Prefs.baiduSecret = it }
        bindEdit(R.id.etDeeplKey, Prefs.deeplKey) { Prefs.deeplKey = it }
    }

    private fun bindEdit(id: Int, current: String, setter: (String) -> Unit) {
        val et = findViewById<EditText>(id)
        et.setText(current)
        et.doAfterTextChanged { setter(it?.toString()?.trim().orEmpty()) }
    }
}
