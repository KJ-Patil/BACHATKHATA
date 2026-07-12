package com.example.bachatkhata;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.bachatkhata.databinding.FragmentDataToolsBinding;

import java.util.Arrays;
import java.util.List;

/**
 * Grid screen surfacing all power-user tools that previously lived only in the
 * Settings "Data &amp; Tools" list. Destinations are kept 1:1 with
 * {@link ProfileFragment} so there is a single source of truth for each tool.
 */
public class DataToolsFragment extends Fragment {

    private FragmentDataToolsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDataToolsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        ToolsGridAdapter adapter = new ToolsGridAdapter(buildTools(), this::onToolClick);
        binding.rvDataTools.setAdapter(adapter);
    }

    private List<ToolsGridAdapter.ToolItem> buildTools() {
        return Arrays.asList(
                ToolsGridAdapter.ToolItem.nav("👨‍👩‍👧", "Family Wallet", R.id.navigation_family_wallet),
                ToolsGridAdapter.ToolItem.activity("📄", "Export Reports", ExportActivity.class),
                ToolsGridAdapter.ToolItem.nav("🔁", "Subscriptions", R.id.navigation_subscriptions),
                ToolsGridAdapter.ToolItem.nav("🏦", "EMI & Loans", R.id.navigation_emi_tracker),
                ToolsGridAdapter.ToolItem.activity("📅", "Bill Calendar", BillCalendarActivity.class),
                ToolsGridAdapter.ToolItem.nav("🏆", "Achievements", R.id.navigation_achievements),
                ToolsGridAdapter.ToolItem.nav("❤️", "Health Score", R.id.navigation_health_score),
                ToolsGridAdapter.ToolItem.activity("💰", "Net Worth", NetWorthActivity.class),
                ToolsGridAdapter.ToolItem.nav("🪙", "Round-Up", R.id.navigation_roundup_settings),
                ToolsGridAdapter.ToolItem.activity("📊", "Credit Simulator", CibilSimulatorActivity.class),
                ToolsGridAdapter.ToolItem.activity("🔮", "What-If Simulator", WhatIfSimulatorActivity.class),
                ToolsGridAdapter.ToolItem.activity("📈", "Month Comparison", ComparisonActivity.class),
                ToolsGridAdapter.ToolItem.activity("🧾", "Bill Splitter", BillSplitterActivity.class),
                ToolsGridAdapter.ToolItem.activity("😊", "Money & Mood", MoodInsightActivity.class),
                ToolsGridAdapter.ToolItem.nav("🎓", "Financial School", R.id.navigation_literacy_lessons),
                ToolsGridAdapter.ToolItem.nav("🌱", "Carbon Footprint", R.id.navigation_carbon_tracker),
                ToolsGridAdapter.ToolItem.activity("📁", "Manage Categories", CategoryManageActivity.class)
        );
    }

    private void onToolClick(ToolsGridAdapter.ToolItem tool) {
        if (tool.navDestId != 0) {
            Navigation.findNavController(requireView()).navigate(tool.navDestId);
        } else if (tool.activityClass != null) {
            startActivity(new Intent(requireContext(), tool.activityClass));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
