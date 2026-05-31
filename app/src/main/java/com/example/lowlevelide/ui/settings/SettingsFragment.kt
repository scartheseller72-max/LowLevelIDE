package com.example.lowlevelide.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lowlevelide.App
import com.example.lowlevelide.R
import com.example.lowlevelide.bootstrap.BootstrapInstaller
import com.example.lowlevelide.databinding.FragmentSettingsBinding
import com.example.lowlevelide.settings.AppSettings
import com.example.lowlevelide.util.CrashHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * All editable preferences. Backed by [AppSettings] (DataStore). Most sliders/switches/
 * dropdowns write back asynchronously via the App-scope coroutine.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val app = requireActivity().applicationContext as App
        val settings = app.settings

        // Dropdown item label arrays (ordered to match the key lists).
        val editorThemeKeys = AppSettings.EDITOR_THEMES.keys.toList()
        val editorThemeLabels = AppSettings.EDITOR_THEMES.values.toTypedArray()
        val paletteKeys = AppSettings.TERMINAL_PALETTES.keys.toList()
        val paletteLabels = AppSettings.TERMINAL_PALETTES.values.toTypedArray()
        binding.editorThemeDropdown.setSimpleItems(editorThemeLabels)
        binding.terminalPaletteDropdown.setSimpleItems(paletteLabels)

        viewLifecycleOwner.lifecycleScope.launch {
            // Initial values
            when (settings.themeFlow.first()) {
                AppSettings.THEME_LIGHT -> binding.themeLight.isChecked = true
                AppSettings.THEME_DARK -> binding.themeDark.isChecked = true
                else -> binding.themeSystem.isChecked = true
            }
            binding.editorFontSlider.value = settings.editorFontSizeFlow.first().toFloat()
            binding.terminalFontSlider.value = settings.terminalFontSizeFlow.first().toFloat()
            binding.extraKeysSwitch.isChecked = settings.extraKeysFlow.first()
            binding.useRootSwitch.isChecked = settings.useRootFlow.first()
            binding.useProotSwitch.isChecked = settings.useProotFlow.first()
            binding.persistentTerminalSwitch.isChecked = settings.persistentTerminalFlow.first()
            binding.wordWrapSwitch.isChecked = settings.wordWrapFlow.first()

            val themeKey = settings.editorThemeFlow.first()
            val themeIdx = editorThemeKeys.indexOf(themeKey).coerceAtLeast(0)
            binding.editorThemeDropdown.setText(editorThemeLabels[themeIdx], false)

            val paletteKey = settings.terminalPaletteFlow.first()
            val paletteIdx = paletteKeys.indexOf(paletteKey).coerceAtLeast(0)
            binding.terminalPaletteDropdown.setText(paletteLabels[paletteIdx], false)
        }

        binding.themeGroup.setOnCheckedChangeListener { _, id ->
            val key = when (id) {
                R.id.themeLight -> AppSettings.THEME_LIGHT
                R.id.themeDark -> AppSettings.THEME_DARK
                else -> AppSettings.THEME_SYSTEM
            }
            app.applicationScope.launch { settings.setTheme(key) }
        }
        binding.editorFontSlider.addOnChangeListener { _, value, _ ->
            app.applicationScope.launch { settings.setEditorFontSize(value.toInt()) }
        }
        binding.terminalFontSlider.addOnChangeListener { _, value, _ ->
            app.applicationScope.launch { settings.setTerminalFontSize(value.toInt()) }
        }
        binding.editorThemeDropdown.setOnItemClickListener { _, _, position, _ ->
            val key = editorThemeKeys.getOrElse(position) { AppSettings.DEFAULT_EDITOR_THEME }
            app.applicationScope.launch { settings.setEditorTheme(key) }
        }
        binding.terminalPaletteDropdown.setOnItemClickListener { _, _, position, _ ->
            val key = paletteKeys.getOrElse(position) { AppSettings.DEFAULT_TERMINAL_PALETTE }
            app.applicationScope.launch { settings.setTerminalPalette(key) }
        }
        binding.wordWrapSwitch.setOnCheckedChangeListener { _, checked ->
            app.applicationScope.launch { settings.setWordWrap(checked) }
        }
        binding.extraKeysSwitch.setOnCheckedChangeListener { _, checked ->
            app.applicationScope.launch { settings.setExtraKeys(checked) }
        }
        binding.useRootSwitch.setOnCheckedChangeListener { _, checked ->
            app.applicationScope.launch { settings.setUseRoot(checked) }
        }
        binding.useProotSwitch.setOnCheckedChangeListener { _, checked ->
            app.applicationScope.launch { settings.setUseProot(checked) }
        }
        binding.persistentTerminalSwitch.setOnCheckedChangeListener { _, checked ->
            app.applicationScope.launch { settings.setPersistentTerminal(checked) }
        }

        binding.btnReinstallBootstrap.setOnClickListener { reinstallBootstrap() }
        binding.btnClearData.setOnClickListener { clearAppData() }
        binding.btnViewCrash.setOnClickListener { showLatestCrash() }
        binding.btnClearCrash.setOnClickListener {
            CrashHandler.clear(requireContext())
            Toast.makeText(requireContext(), R.string.crash_cleared, Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }

    private fun showLatestCrash() {
        val report = CrashHandler.latestReport(requireContext())
        if (report == null) {
            Toast.makeText(requireContext(), R.string.settings_no_crash, Toast.LENGTH_SHORT).show()
            return
        }
        val text = report.readText().let { if (it.length > 4000) it.substring(0, 4000) + "\n…(truncated)" else it }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.crash_dialog_title)
            .setMessage(text)
            .setPositiveButton(R.string.crash_share) { _, _ -> shareCrash(report) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareCrash(report: java.io.File) {
        val authority = requireContext().packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(requireContext(), authority, report)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_share_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.crash_share)))
    }

    private fun reinstallBootstrap() {
        BootstrapInstaller.reset(requireContext())
        val app = requireActivity().applicationContext as App
        app.applicationScope.launch { app.settings.setOnboarded(false) }
        requireActivity().recreate()
    }

    private fun clearAppData() {
        BootstrapInstaller.reset(requireContext())
        val home = BootstrapInstaller.homeDir(requireContext())
        home.deleteRecursively()
        val app = requireActivity().applicationContext as App
        app.applicationScope.launch { app.settings.setOnboarded(false) }
        requireActivity().recreate()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
