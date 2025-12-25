package com.nuist.setu.killbill.ui.fragment;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.Snackbar;
import com.nuist.setu.killbill.data.Bill;
import com.nuist.setu.killbill.databinding.FragmentDailyBinding;
import com.nuist.setu.killbill.ui.AddEditBillActivity;
import com.nuist.setu.killbill.ui.adapter.BillAdapter;
import com.nuist.setu.killbill.ui.viewmodel.DailyViewModel;
import com.nuist.setu.killbill.util.DateTimeUtils;
import com.nuist.setu.killbill.util.MoneyUtils;

import java.util.Calendar;
import java.util.List;

public class DailyFragment extends Fragment {

    private FragmentDailyBinding binding;

    private DailyViewModel viewModel;
    private BillAdapter adapter;

    private Bill lastDeleted = null;

    public DailyFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDailyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new BillAdapter(bill -> openEdit(bill.id));

        binding.recyclerBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBills.setAdapter(adapter);

        attachSwipeToDelete(binding.recyclerBills);

        viewModel = new ViewModelProvider(this).get(DailyViewModel.class);

        viewModel.getSelectedDayStart().observe(getViewLifecycleOwner(), dayStart -> {
            binding.tvDate.setText(DateTimeUtils.formatDate(dayStart));
        });

        viewModel.getBills().observe(getViewLifecycleOwner(), bills -> {
            adapter.submitList(bills);
        });

        viewModel.getTotalAmount().observe(getViewLifecycleOwner(), total -> {
            binding.tvTotal.setText(MoneyUtils.formatCny(total));
        });

        binding.tvDate.setOnClickListener(v -> pickDate());
        binding.fabAdd.setOnClickListener(v -> openAdd());
    }

    private void attachSwipeToDelete(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback cb = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                List<Bill> currentList = adapter.getCurrentList();
                if (pos < 0 || pos >= currentList.size()) return;

                Bill bill = currentList.get(pos);
                lastDeleted = bill;
                viewModel.delete(bill);

                Snackbar.make(binding.getRoot(), "Deleted", Snackbar.LENGTH_LONG)
                        .setAction("Cancel", v -> {
                            if (lastDeleted != null) {
                                viewModel.insert(lastDeleted);
                                lastDeleted = null;
                            }
                        })
                        .show();
            }
        };
        new ItemTouchHelper(cb).attachToRecyclerView(recyclerView);
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(viewModel.getSelectedDayStart().getValue() == null
                ? System.currentTimeMillis()
                : viewModel.getSelectedDayStart().getValue());

        DatePickerDialog dp = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    picked.set(Calendar.HOUR_OF_DAY, 0);
                    picked.set(Calendar.MINUTE, 0);
                    picked.set(Calendar.SECOND, 0);
                    picked.set(Calendar.MILLISECOND, 0);

                    viewModel.setSelectedDate(picked.getTimeInMillis());
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    private void openAdd() {
        Intent intent = new Intent(requireContext(), AddEditBillActivity.class);
        startActivity(intent);
    }

    private void openEdit(long billId) {
        Intent intent = new Intent(requireContext(), AddEditBillActivity.class);
        intent.putExtra(AddEditBillActivity.EXTRA_BILL_ID, billId);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
