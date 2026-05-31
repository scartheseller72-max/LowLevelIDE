package com.example.lowlevelide.ui.filebrowser

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.lowlevelide.R
import com.example.lowlevelide.databinding.ItemFileTreeBinding
import java.io.File

/**
 * Flat-list rendering of a directory tree. We keep things simple by collapsing/expanding
 * directories in-place rather than implementing a real tree model. Each row is annotated
 * with a depth so we can pad the indent.
 */
class FileTreeAdapter(
    private val root: File,
    private val onFileTap: (File) -> Unit,
    private val onLongPress: (File, View) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.VH>() {

    private data class Row(val file: File, val depth: Int)

    private val rows = mutableListOf<Row>()
    private val expanded = mutableSetOf<String>()

    init { refresh() }

    fun refresh() {
        rows.clear()
        appendDir(root, 0)
        notifyDataSetChanged()
    }

    private fun appendDir(dir: File, depth: Int) {
        val entries = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
        entries.forEach { entry ->
            rows.add(Row(entry, depth))
            if (entry.isDirectory && expanded.contains(entry.absolutePath)) {
                appendDir(entry, depth + 1)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileTreeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        val file = row.file
        holder.binding.indent.layoutParams = holder.binding.indent.layoutParams.apply {
            width = (row.depth * 16) + 4
        }
        holder.binding.icon.setImageResource(if (file.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
        holder.binding.name.text = file.name
        holder.binding.size.text = if (file.isFile)
            Formatter.formatShortFileSize(holder.binding.root.context, file.length())
        else ""
        holder.binding.root.setOnClickListener {
            if (file.isDirectory) {
                if (expanded.contains(file.absolutePath)) expanded.remove(file.absolutePath)
                else expanded.add(file.absolutePath)
                refresh()
            } else {
                onFileTap(file)
            }
        }
        holder.binding.root.setOnLongClickListener {
            onLongPress(file, it); true
        }
    }

    class VH(val binding: ItemFileTreeBinding) : RecyclerView.ViewHolder(binding.root)
}
