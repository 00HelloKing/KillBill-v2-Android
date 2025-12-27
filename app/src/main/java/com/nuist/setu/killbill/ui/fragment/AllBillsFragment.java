package com.nuist.setu.killbill.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
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
import com.nuist.setu.killbill.databinding.FragmentAllBillsBinding;
import com.nuist.setu.killbill.ui.AddEditBillActivity;
import com.nuist.setu.killbill.ui.adapter.BillAdapter;
import com.nuist.setu.killbill.ui.viewmodel.AllBillsViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AllBillsFragment extends Fragment {

    private FragmentAllBillsBinding binding;

    private AllBillsViewModel viewModel;
    private BillAdapter adapter;

    private List<Bill> fullList = new ArrayList<>();
    private String currentQuery = "";

    private Bill lastDeleted = null;

    public AllBillsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAllBillsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new BillAdapter(bill -> openEdit(bill.id));
        binding.recyclerAllBills.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAllBills.setAdapter(adapter);

        attachSwipeToDelete(binding.recyclerAllBills);

        viewModel = new ViewModelProvider(this).get(AllBillsViewModel.class);

        viewModel.getAllBills().observe(getViewLifecycleOwner(), bills -> {
            fullList = bills == null ? new ArrayList<>() : bills;
            applyFilter(currentQuery);
        });

        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query == null ? "" : query;
                applyFilter(currentQuery);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText == null ? "" : newText;
                applyFilter(currentQuery);
                return true;
            }
        });

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

    private void applyFilter(String query) {
        if (TextUtils.isEmpty(query)) {
            adapter.submitList(new ArrayList<>(fullList));
            return;
        }

        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Bill> filtered = new ArrayList<>();
        for (Bill b : fullList) {
            String note = b.note == null ? "" : b.note;
            String cat = b.category == null ? "" : b.category;
            String src = b.source == null ? "" : b.source;
            if (note.toLowerCase(Locale.ROOT).contains(q)
                    || cat.toLowerCase(Locale.ROOT).contains(q)
                    || src.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(b);
            }
        }
        adapter.submitList(filtered);
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
