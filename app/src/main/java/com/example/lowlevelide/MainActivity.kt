package com.example.lowlevelide

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lowlevelide.databinding.ActivityMainBinding
import com.example.lowlevelide.system.RootDetect
import com.example.lowlevelide.terminal.PtyService
import com.example.lowlevelide.ui.about.AboutFragment
import com.example.lowlevelide.ui.editor.EditorFragment
import com.example.lowlevelide.ui.filebrowser.FileBrowserFragment
import com.example.lowlevelide.ui.onboarding.OnboardingActivity
import com.example.lowlevelide.ui.settings.SettingsFragment
import com.example.lowlevelide.ui.terminal.TerminalFragment
import com.example.lowlevelide.util.NotificationPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main shell of the IDE. Hosts:
 *  - DrawerLayout (file browser + nav) on the left
 *  - Editor fragment in the upper container
 *  - Terminal fragment in the lower container
 *
 * If the user hasn't completed onboarding (or bootstrap is missing), we redirect to
 * [OnboardingActivity] before doing anything else.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notifications = NotificationPermissionHelper(this)

    private var ptyService: PtyService? = null
    private val ptyConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            ptyService = (service as PtyService.LocalBinder).getService()
            // Hand the bound service to whichever terminal fragment is live.
            (supportFragmentManager.findFragmentById(R.id.terminalContainer) as? TerminalFragment)
                ?.attachService(ptyService!!)
        }
        override fun onServiceDisconnected(name: ComponentName?) { ptyService = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = applicationContext as App
        lifecycleScope.launch {
            // Redirect to onboarding only when the user hasn't completed it. We intentionally
            // do NOT also require an installed bootstrap here: "Skip (offline mode)" marks the
            // user onboarded without downloading a rootfs, and gating on isInstalled() would
            // bounce those users back to onboarding forever. When the bootstrap is absent the
            // terminal falls back to /system/bin/sh and the editor still opens, so the shell
            // degrades gracefully instead of trapping the user.
            val onboarded = app.settings.onboardedFlow.first()
            if (!onboarded) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                finish()
                return@launch
            }
            initialiseShell()
        }
    }

    private fun initialiseShell() {
        notifications.ensureGranted()
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Drawer nav.
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_files -> showFragment(FileBrowserFragment(), R.id.editorContainer)
                R.id.nav_editor -> showFragment(EditorFragment(), R.id.editorContainer)
                R.id.nav_terminal -> showFragment(TerminalFragment(), R.id.terminalContainer)
                R.id.nav_settings -> showFragment(SettingsFragment(), R.id.editorContainer)
                R.id.nav_about -> showFragment(AboutFragment(), R.id.editorContainer)
            }
            binding.drawerLayout.closeDrawers()
            true
        }

        // Toolbar overflow menu.
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> { editorFragment()?.saveActive(); true }
                R.id.action_run -> { editorFragment()?.buildAndRunActive(); true }
                R.id.action_save_all -> { editorFragment()?.saveAll(); true }
                R.id.action_find -> { editorFragment()?.openFind(); true }
                R.id.action_settings -> { showFragment(SettingsFragment(), R.id.editorContainer); true }
                R.id.action_about -> { showFragment(AboutFragment(), R.id.editorContainer); true }
                else -> false
            }
        }

        // Splitter drag.
        attachSplitterBehaviour()

        // Initial fragments.
        if (supportFragmentManager.findFragmentById(R.id.editorContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.editorContainer, EditorFragment())
                .replace(R.id.terminalContainer, TerminalFragment())
                .commit()
        }

        // Keep terminals alive in background if user wants.
        lifecycleScope.launch {
            val persistent = (applicationContext as App).settings.persistentTerminalFlow.first()
            if (persistent) {
                PtyService.start(this@MainActivity)
                bindService(
                    Intent(this@MainActivity, PtyService::class.java),
                    ptyConnection,
                    Context.BIND_AUTO_CREATE
                )
            }
        }

        updateRootBadge()
    }

    private fun showFragment(fragment: Fragment, containerId: Int) {
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commit()
    }

    private fun editorFragment(): EditorFragment? =
        supportFragmentManager.findFragmentById(R.id.editorContainer) as? EditorFragment

    private fun updateRootBadge() {
        // RootDetect.isRootAvailable() may exec `su` and block for up to 5s; never do that on
        // the UI thread (it would ANR). Detect on IO, then update the badge back on Main.
        lifecycleScope.launch {
            val isRoot = withContext(Dispatchers.IO) { RootDetect.isRootAvailable() }
            binding.badgeRoot.text = getString(if (isRoot) R.string.badge_root else R.string.badge_user)
            binding.badgeRoot.setBackgroundColor(
                getColor(if (isRoot) R.color.badge_root else R.color.badge_user)
            )
        }
    }

    /**
     * Drag the divider between editor and terminal to resize each pane.
     * We mutate the layout_weight of both LinearLayout children.
     */
    private fun attachSplitterBehaviour() {
        var startY = 0f
        var startEditorH = 0
        var startTermH = 0
        binding.splitter.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    startEditorH = binding.editorContainer.height
                    startTermH = binding.terminalContainer.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = (e.rawY - startY).toInt()
                    val newEditor = (startEditorH + delta).coerceAtLeast(120)
                    val newTerm = (startTermH - delta).coerceAtLeast(120)
                    binding.editorContainer.layoutParams =
                        binding.editorContainer.layoutParams.apply {
                            height = newEditor
                            (this as android.widget.LinearLayout.LayoutParams).weight = 0f
                        }
                    binding.terminalContainer.layoutParams =
                        binding.terminalContainer.layoutParams.apply {
                            height = newTerm
                            (this as android.widget.LinearLayout.LayoutParams).weight = 0f
                        }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        runCatching { unbindService(ptyConnection) }
        super.onDestroy()
    }
}
