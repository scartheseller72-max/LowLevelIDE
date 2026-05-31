package com.example.lowlevelide.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lowlevelide.R
import com.example.lowlevelide.databinding.PageOnboardingBinding

class OnboardingPageFragment : Fragment(R.layout.page_onboarding) {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val binding = PageOnboardingBinding.inflate(inflater, container, false)
        val titleRes = requireArguments().getInt(KEY_TITLE)
        val bodyRes = requireArguments().getInt(KEY_BODY)
        val iconRes = requireArguments().getInt(KEY_ICON)
        binding.pageTitle.text = getString(titleRes)
        binding.pageBody.text = getString(bodyRes)
        binding.pageIcon.setImageResource(iconRes)
        return binding.root
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_ICON = "icon"

        fun newInstance(page: OnboardingPage) = OnboardingPageFragment().apply {
            arguments = Bundle().apply {
                putInt(KEY_TITLE, page.titleRes)
                putInt(KEY_BODY, page.bodyRes)
                putInt(KEY_ICON, page.iconRes)
            }
        }
    }
}
