package com.copytranslate.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.copytranslate.app.R
import com.copytranslate.app.db.HistoryRepository
import com.copytranslate.app.db.TranslationHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录页：RecyclerView 列表，长按删除单条，顶部"清空"按钮。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        setTitle(R.string.history_title)

        adapter = HistoryAdapter { item -> showDeleteDialog(item) }
        findViewById<RecyclerView>(R.id.recyclerHistory).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }
        tvEmpty = findViewById(R.id.tvEmpty)
        findViewById<TextView>(R.id.btnClear).setOnClickListener { showClearDialog() }
        reload()
    }

    private fun reload() {
        adapter.submit(HistoryRepository.getAll())
        tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun showDeleteDialog(item: TranslationHistory) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_msg)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                HistoryRepository.delete(item.id)
                reload()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showClearDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_msg)
            .setPositiveButton(R.string.btn_clear) { _, _ ->
                HistoryRepository.clear()
                reload()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}

/**
 * 历史列表适配器：原文（灰小字）/ 译文（大字）/ 时间（更小灰字），长按回调删除。
 */
class HistoryAdapter(
    private val onLongClick: (TranslationHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val data = mutableListOf<TranslationHistory>()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun submit(list: List<TranslationHistory>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSource: TextView = v.findViewById(R.id.tvItemSource)
        val tvTarget: TextView = v.findViewById(R.id.tvItemTarget)
        val tvTime: TextView = v.findViewById(R.id.tvItemTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.tvSource.text = item.sourceText
        holder.tvTarget.text = item.translatedText
        holder.tvTime.text = timeFormat.format(Date(item.createTime))
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = data.size
}
