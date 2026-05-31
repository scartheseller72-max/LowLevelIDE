package com.example.lowlevelide.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lowlevelide.BuildConfig
import com.example.lowlevelide.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        binding.aboutVersion.text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        return binding.root
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
