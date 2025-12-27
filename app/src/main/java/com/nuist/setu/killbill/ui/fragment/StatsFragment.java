package com.nuist.setu.killbill.ui.fragment;

import android.app.DatePickerDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.color.MaterialColors;
import com.nuist.setu.killbill.data.CategoryTotal;
import com.nuist.setu.killbill.databinding.FragmentStatsBinding;
import com.nuist.setu.killbill.ui.adapter.CategoryTotalAdapter;
import com.nuist.setu.killbill.ui.viewmodel.StatsViewModel;
import com.nuist.setu.killbill.util.DateTimeUtils;
import com.nuist.setu.killbill.util.MoneyUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;

    private StatsViewModel viewModel;
    private CategoryTotalAdapter adapter;

    public StatsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CategoryTotalAdapter();
        binding.recyclerCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCategories.setAdapter(adapter);

        setupPieChart();

        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);

        viewModel.getSelectedMonthStart().observe(getViewLifecycleOwner(), monthStart -> {
            binding.tvMonth.setText(DateTimeUtils.formatMonth(monthStart));
        });

        viewModel.getCategoryTotals().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            updatePieChart(list);
        });

        binding.tvMonth.setOnClickListener(v -> pickMonth());
    }

    private void setupPieChart() {
        binding.pieChart.getDescription().setEnabled(false);
        binding.pieChart.setUsePercentValues(true);
        binding.pieChart.setDrawEntryLabels(false);
        binding.pieChart.setCenterText("Expenses for this month");
        binding.pieChart.setCenterTextSize(14f);
        binding.pieChart.setHoleRadius(55f);
        binding.pieChart.setTransparentCircleRadius(60f);
        binding.pieChart.getLegend().setEnabled(true);

        // 让图例文字跟随主题
        int textColor = MaterialColors.getColor(binding.pieChart,
                com.google.android.material.R.attr.colorOnBackground);
        binding.pieChart.getLegend().setTextColor(textColor);

    }

    private void updatePieChart(List<CategoryTotal> list) {
        if (list == null || list.isEmpty()) {
            binding.pieChart.clear();
            binding.pieChart.setCenterText("No Data");
            binding.pieChart.invalidate();
            return;
        }

        double sum = 0;
        for (CategoryTotal ct : list) {
            sum += ct.total;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (CategoryTotal ct : list) {
            float percent = (float) (ct.total / sum * 100.0);
            entries.add(new PieEntry(percent, ct.category));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        binding.pieChart.setData(data);
        binding.pieChart.setCenterText("Expenses for this month\n" + MoneyUtils.formatCny(sum));
        binding.pieChart.invalidate();
    }

    private void pickMonth() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(viewModel.getSelectedMonthStart().getValue() == null
                ? System.currentTimeMillis()
                : viewModel.getSelectedMonthStart().getValue());

        DatePickerDialog dp = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, 1);
                    picked.set(Calendar.HOUR_OF_DAY, 0);
                    picked.set(Calendar.MINUTE, 0);
                    picked.set(Calendar.SECOND, 0);
                    picked.set(Calendar.MILLISECOND, 0);

                    viewModel.setSelectedMonth(picked.getTimeInMillis());
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
