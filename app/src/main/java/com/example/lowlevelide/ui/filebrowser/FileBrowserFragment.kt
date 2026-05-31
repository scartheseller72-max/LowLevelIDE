package com.example.lowlevelide.ui.filebrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lowlevelide.R
import com.example.lowlevelide.databinding.FragmentFilebrowserBinding
import com.example.lowlevelide.files.FileRepository
import java.io.File

/**
 * Tree-style file browser. Single click expands a directory or asks the editor to open
 * a file. Long-press opens a context menu (rename / delete / open in terminal).
 * The toolbar also exposes a Storage Access Framework importer and a quick Git menu
 * that drives the bottom terminal pane.
 */
class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFilebrowserBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileTreeAdapter
    private lateinit var repo: FileRepository

    /** SAF document picker — copies the chosen file into the sandbox home dir. */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val created = repo.importFromUri(uri)
        adapter.refresh()
        val msg = if (created != null)
            getString(R.string.file_imported, created.name)
        else getString(R.string.file_import_failed)
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentFilebrowserBinding.inflate(inflater, container, false)
        repo = FileRepository(requireContext())
        adapter = FileTreeAdapter(
            root = repo.homeRoot,
            onFileTap = { file -> openInEditor(file) },
            onLongPress = { file, anchor -> showContextMenu(file, anchor) }
        )
        binding.fileTreeRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.fileTreeRecycler.adapter = adapter
        binding.currentPath.text = repo.homeRoot.absolutePath
        binding.btnRefresh.setOnClickListener { adapter.refresh() }
        binding.btnNewFolder.setOnClickListener { promptCreate(isFolder = true) }
        binding.btnNewFile.setOnClickListener { promptCreate(isFolder = false) }
        binding.btnImport.setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
        binding.btnGit.setOnClickListener { showGitMenu(it) }
        return binding.root
    }

    /** Quick Git actions, executed in the active terminal session against $HOME. */
    private fun showGitMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "git status")
        popup.menu.add(0, 2, 1, "git add -A")
        popup.menu.add(0, 3, 2, "git commit…")
        popup.menu.add(0, 4, 3, "git log --oneline -20")
        popup.menu.add(0, 5, 4, "git pull")
        popup.menu.add(0, 6, 5, "git push")
        popup.menu.add(0, 7, 6, "git init")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> sendGit("git status")
                2 -> sendGit("git add -A && git status --short")
                3 -> promptCommit()
                4 -> sendGit("git log --oneline -20")
                5 -> sendGit("git pull")
                6 -> sendGit("git push")
                7 -> sendGit("git init")
            }
            true
        }
        popup.show()
    }

    private fun promptCommit() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.git_commit_hint)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.git_commit_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val msg = input.text.toString().ifBlank { "update" }
                    .replace("\"", "\\\"")
                sendGit("git commit -m \"$msg\"")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendGit(gitCommand: String) {
        val term = requireActivity().supportFragmentManager
            .findFragmentById(R.id.terminalContainer)
            as? com.example.lowlevelide.ui.terminal.TerminalFragment
        if (term == null) {
            Toast.makeText(requireContext(), R.string.git_no_terminal, Toast.LENGTH_SHORT).show()
            return
        }
        term.sendToActiveSession("cd \"\$HOME\" && $gitCommand\n")
    }

    private fun promptCreate(isFolder: Boolean) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx)
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(if (isFolder) com.example.lowlevelide.R.string.file_create_folder else com.example.lowlevelide.R.string.file_create_file))
            .setView(input)
            .setPositiveButton(com.example.lowlevelide.R.string.ok) { _, _ ->
                val name = input.text.toString()
                if (isFolder) repo.mkdir(repo.homeRoot, name)
                else repo.touch(repo.homeRoot, name)
                adapter.refresh()
            }
            .setNegativeButton(com.example.lowlevelide.R.string.cancel, null)
            .show()
    }

    private fun openInEditor(file: File) {
        val rel = file.relativeTo(repo.homeRoot).path
        val activity = requireActivity()
        val editor = activity.supportFragmentManager.findFragmentById(com.example.lowlevelide.R.id.editorContainer)
            as? com.example.lowlevelide.ui.editor.EditorFragment ?: return
        editor.requireView().findViewById<android.webkit.WebView>(com.example.lowlevelide.R.id.webviewEditor)
            ?.evaluateJavascript("openFile(${quote(rel)});", null)
    }

    private fun showContextMenu(file: File, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.inflate(com.example.lowlevelide.R.menu.menu_file_context)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.example.lowlevelide.R.id.ctx_open -> { openInEditor(file); true }
                com.example.lowlevelide.R.id.ctx_run -> { runInTerminal(file); true }
                com.example.lowlevelide.R.id.ctx_rename -> { renameDialog(file); true }
                com.example.lowlevelide.R.id.ctx_delete -> { deleteDialog(file); true }
                com.example.lowlevelide.R.id.ctx_copy_path -> { copyPath(file); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun runInTerminal(file: File) {
        val rel = file.relativeTo(repo.homeRoot).path
        val activity = requireActivity()
        val term = activity.supportFragmentManager.findFragmentById(com.example.lowlevelide.R.id.terminalContainer)
            as? com.example.lowlevelide.ui.terminal.TerminalFragment
        term?.sendToActiveSession("./\"$rel\"\n")
    }

    private fun renameDialog(file: File) {
        val input = android.widget.EditText(requireContext()).apply { setText(file.name) }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(com.example.lowlevelide.R.string.file_rename)
            .setView(input)
            .setPositiveButton(com.example.lowlevelide.R.string.ok) { _, _ ->
                repo.rename(file, input.text.toString())
                adapter.refresh()
            }
            .setNegativeButton(com.example.lowlevelide.R.string.cancel, null)
            .show()
    }

    private fun deleteDialog(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(com.example.lowlevelide.R.string.file_delete)
            .setMessage(getString(com.example.lowlevelide.R.string.file_confirm_delete, file.name))
            .setPositiveButton(com.example.lowlevelide.R.string.ok) { _, _ ->
                repo.delete(file); adapter.refresh()
            }
            .setNegativeButton(com.example.lowlevelide.R.string.cancel, null)
            .show()
    }

    private fun copyPath(file: File) {
        val mgr = requireContext().getSystemService(android.content.ClipboardManager::class.java)
        mgr?.setPrimaryClip(android.content.ClipData.newPlainText("path", file.absolutePath))
    }

    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
