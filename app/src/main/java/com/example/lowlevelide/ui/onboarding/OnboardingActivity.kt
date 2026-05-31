package com.example.lowlevelide.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.lowlevelide.App
import com.example.lowlevelide.MainActivity
import com.example.lowlevelide.R
import com.example.lowlevelide.bootstrap.AssetDownloader
import com.example.lowlevelide.bootstrap.BootstrapInstaller
import com.example.lowlevelide.bootstrap.CodeMirrorInstaller
import com.example.lowlevelide.bootstrap.PRootInstaller
import com.example.lowlevelide.databinding.ActivityOnboardingBinding
import com.example.lowlevelide.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 3-page onboarding wizard:
 *  1. Welcome
 *  2. Setup downloads (Alpine + PRoot + CodeMirror)
 *  3. Done — open the IDE
 *
 * Skip button on page 2 marks onboarded=true without downloads (offline mode).
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val pages = listOf(
        OnboardingPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body, R.drawable.ic_terminal),
        OnboardingPage(R.string.onboarding_downloads_title, R.string.onboarding_downloads_body, R.drawable.ic_refresh),
        OnboardingPage(R.string.onboarding_finish_title, R.string.onboarding_finish_body, R.drawable.ic_run)
    )

    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.onboardingPager.adapter = OnboardingAdapter(this, pages)
        binding.onboardingPager.isUserInputEnabled = false

        binding.btnNext.setOnClickListener { onNextClicked() }
        binding.btnBack.setOnClickListener {
            if (binding.onboardingPager.currentItem > 0) binding.onboardingPager.currentItem--
            updateButtons()
        }
        binding.btnSkip.setOnClickListener { skipDownloads() }
        updateButtons()
    }

    private fun onNextClicked() {
        when (binding.onboardingPager.currentItem) {
            0 -> { binding.onboardingPager.currentItem = 1; updateButtons() }
            1 -> startDownloads()
            2 -> finishOnboarding()
        }
    }

    private fun updateButtons() {
        val page = binding.onboardingPager.currentItem
        binding.btnBack.isEnabled = page > 0 && !downloading
        binding.btnSkip.isEnabled = page == 1 && !downloading
        binding.btnSkip.visibility = if (page == 1) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.btnNext.text = when (page) {
            0 -> getString(R.string.onboarding_next)
            1 -> getString(R.string.onboarding_start)
            2 -> getString(R.string.onboarding_finish)
            else -> getString(R.string.onboarding_next)
        }
    }

    private fun startDownloads() {
        if (downloading) return
        downloading = true
        binding.btnNext.isEnabled = false
        binding.btnBack.isEnabled = false
        binding.btnSkip.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.progressText.text = getString(R.string.onboarding_progress_init)

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                runStep(R.string.onboarding_progress_alpine) {
                    BootstrapInstaller.install(this@OnboardingActivity, onProgress = it)
                }
                runStep(R.string.onboarding_progress_proot) {
                    runCatching { PRootInstaller(this@OnboardingActivity).install(onProgress = it) }
                        .onFailure { e -> Logger.w("Onboarding", "PRoot install failed (continuing): ${e.message}") }
                }
                runStep(R.string.onboarding_progress_codemirror) {
                    runCatching { CodeMirrorInstaller(this@OnboardingActivity).install(onProgress = it) }
                        .onFailure { e -> Logger.w("Onboarding", "CodeMirror install failed (continuing): ${e.message}") }
                }
            }.isSuccess

            withContext(Dispatchers.Main) {
                downloading = false
                if (ok) {
                    binding.progressText.text = getString(R.string.onboarding_progress_done)
                    binding.progressBar.progress = 100
                    binding.onboardingPager.currentItem = 2
                    (applicationContext as App).applicationScope.launch {
                        (applicationContext as App).settings.setOnboarded(true)
                    }
                } else {
                    binding.progressText.text = getString(R.string.onboarding_progress_failed, "see logs")
                }
                updateButtons()
                binding.btnNext.isEnabled = true
            }
        }
    }

    private inline fun runStep(textRes: Int, crossinline block: ((AssetDownloader.Progress) -> Unit) -> Unit) {
        runOnUiThread { binding.progressText.text = getString(textRes) }
        block { p ->
            if (p is AssetDownloader.Progress.Bytes) {
                runOnUiThread {
                    if (p.total > 0) {
                        binding.progressBar.progress = ((p.read * 100) / p.total).toInt()
                    }
                }
            }
        }
    }

    private fun skipDownloads() {
        (applicationContext as App).applicationScope.launch {
            (applicationContext as App).settings.setOnboarded(true)
        }
        finishOnboarding()
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

data class OnboardingPage(val titleRes: Int, val bodyRes: Int, val iconRes: Int)

class OnboardingAdapter(activity: AppCompatActivity, private val pages: List<OnboardingPage>) :
    FragmentStateAdapter(activity) {
    override fun getItemCount() = pages.size
    override fun createFragment(position: Int) = OnboardingPageFragment.newInstance(pages[position])
}
