package com.android.absensi.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.absensi.databinding.FragmentHistoryBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy

class HistoryFragment : Fragment() {
    private var binding: FragmentHistoryBinding? = null
    private var historyPagerAdapter: HistoryPagerAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Setup ViewPager Adapter
        historyPagerAdapter = HistoryPagerAdapter(requireActivity())
        binding!!.viewPager.setAdapter(historyPagerAdapter)

        // Hubungkan TabLayout dengan ViewPager
        TabLayoutMediator(
            binding!!.tabLayout, binding!!.viewPager,
            TabConfigurationStrategy { tab: TabLayout.Tab?, position: Int ->
                when (position) {
                    0 -> tab!!.setText("Riwayat Absensi")
                    1 -> tab!!.setText("Izin & Sakit")
                }
            }
        ).attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
