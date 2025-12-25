package com.nuist.setu.killbill.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.nuist.setu.killbill.R;
import com.nuist.setu.killbill.data.Bill;
import com.nuist.setu.killbill.databinding.ActivityAddEditBillBinding;
import com.nuist.setu.killbill.ui.viewmodel.EditBillViewModel;
import com.nuist.setu.killbill.util.DateTimeUtils;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

/**
 * Add / Edit Bill screen.
 *
 * Improvements:
 * - Uses Room DB via ViewModel/Repository
 * - Supports camera capture for receipt photo (stored in app-specific external files)
 */
public class AddEditBillActivity extends AppCompatActivity {

    public static final String EXTRA_BILL_ID = "extra_bill_id";

    // For auto-capture prefill
    public static final String EXTRA_PREFILL_AMOUNT = "extra_prefill_amount";
    public static final String EXTRA_PREFILL_NOTE = "extra_prefill_note";
    public static final String EXTRA_SOURCE = "extra_source";         // "AUTO" / "MANUAL"
    public static final String EXTRA_PAYMENT_APP = "extra_payment_app"; // "Alipay"/"Wechat"/null

    private ActivityAddEditBillBinding binding;

    private EditBillViewModel viewModel;

    private long editingBillId = -1L;
    private Bill editingBill = null;

    private long selectedTimestampMs = System.currentTimeMillis();
    private String receiptUriString = null;

    private Uri pendingPhotoUri = null;

    private ActivityResultLauncher<String> requestCameraPermission;
    private ActivityResultLauncher<Uri> takePictureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAddEditBillBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(EditBillViewModel.class);

        // Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.bill_categories,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spCategory.setAdapter(adapter);

        // Date/time
        updateDateTimeLabel();

        binding.tvDatetime.setOnClickListener(v -> pickDateTime());
        binding.btnTakePhoto.setOnClickListener(v -> startTakePhotoFlow());
        binding.btnSave.setOnClickListener(v -> onSaveClicked());
        binding.btnDelete.setOnClickListener(v -> onDeleteClicked());

        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchTakePicture();
                    } else {
                        Toast.makeText(this, R.string.camera_permission_msg, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && pendingPhotoUri != null) {
                        receiptUriString = pendingPhotoUri.toString();
                        binding.ivReceipt.setImageURI(pendingPhotoUri);
                    } else {
                        pendingPhotoUri = null;
                    }
                }
        );

        // Check if edit mode
        editingBillId = getIntent().getLongExtra(EXTRA_BILL_ID, -1L);
        if (editingBillId > 0) {
            binding.toolbar.setTitle(R.string.edit_bill);
            binding.btnDelete.setVisibility(android.view.View.VISIBLE);
            observeBill(editingBillId);
        } else {
            binding.toolbar.setTitle(R.string.add_bill);
            applyPrefillIfAny();
        }
    }

    private void observeBill(long id) {
        viewModel.loadBill(id).observe(this, bill -> {
            if (bill == null) return;
            editingBill = bill;

            binding.etAmount.setText(String.format(Locale.CHINA, "%.2f", bill.amount));
            setSpinnerToCategory(bill.category);
            binding.etNote.setText(bill.note == null ? "" : bill.note);

            selectedTimestampMs = bill.timestamp;
            updateDateTimeLabel();

            receiptUriString = bill.receiptUri;
            if (!TextUtils.isEmpty(receiptUriString)) {
                binding.ivReceipt.setImageURI(Uri.parse(receiptUriString));
            } else {
                binding.ivReceipt.setImageDrawable(null);
            }
        });
    }

    private void applyPrefillIfAny() {
        double prefillAmount = getIntent().getDoubleExtra(EXTRA_PREFILL_AMOUNT, Double.NaN);
        String prefillNote = getIntent().getStringExtra(EXTRA_PREFILL_NOTE);

        if (!Double.isNaN(prefillAmount)) {
            binding.etAmount.setText(String.format(Locale.CHINA, "%.2f", prefillAmount));
        }
        if (!TextUtils.isEmpty(prefillNote)) {
            binding.etNote.setText(prefillNote);
        }
    }

    private void setSpinnerToCategory(@NonNull String category) {
        for (int i = 0; i < binding.spCategory.getCount(); i++) {
            if (category.equals(binding.spCategory.getItemAtPosition(i))) {
                binding.spCategory.setSelection(i);
                return;
            }
        }
    }

    private void pickDateTime() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedTimestampMs);

        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar c2 = Calendar.getInstance();
                    c2.setTimeInMillis(selectedTimestampMs);
                    c2.set(Calendar.YEAR, year);
                    c2.set(Calendar.MONTH, month);
                    c2.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    // After date, pick time
                    TimePickerDialog tp = new TimePickerDialog(
                            this,
                            (tv, hourOfDay, minute) -> {
                                c2.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                c2.set(Calendar.MINUTE, minute);
                                c2.set(Calendar.SECOND, 0);
                                c2.set(Calendar.MILLISECOND, 0);

                                selectedTimestampMs = c2.getTimeInMillis();
                                updateDateTimeLabel();
                            },
                            c2.get(Calendar.HOUR_OF_DAY),
                            c2.get(Calendar.MINUTE),
                            true
                    );
                    tp.show();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    private void updateDateTimeLabel() {
        binding.tvDatetime.setText(DateTimeUtils.formatDateTime(selectedTimestampMs));
    }

    private void startTakePhotoFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchTakePicture();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchTakePicture() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir != null && !dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }

            File photo = new File(dir, "receipt_" + System.currentTimeMillis() + ".jpg");
            pendingPhotoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photo
            );

            takePictureLauncher.launch(pendingPhotoUri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to start camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void onSaveClicked() {
        String amountText = binding.etAmount.getText() == null ? "" : binding.etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountText)) {
            binding.tilAmount.setError("Please enter the amount");
            return;
        }
        binding.tilAmount.setError(null);

        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (Exception e) {
            binding.tilAmount.setError("Incorrect format");
            return;
        }
        if (amount <= 0) {
            binding.tilAmount.setError("Must be greater than 0");
            return;
        }

        String category = String.valueOf(binding.spCategory.getSelectedItem());
        String note = binding.etNote.getText() == null ? "" : binding.etNote.getText().toString().trim();

        String source = getIntent().getStringExtra(EXTRA_SOURCE);
        if (TextUtils.isEmpty(source)) source = "MANUAL";

        String paymentApp = getIntent().getStringExtra(EXTRA_PAYMENT_APP);

        if (editingBill != null) {
            editingBill.amount = amount;
            editingBill.category = category;
            editingBill.note = TextUtils.isEmpty(note) ? null : note;
            editingBill.timestamp = selectedTimestampMs;
            editingBill.receiptUri = receiptUriString;
            // keep source/paymentApp unchanged if already exists, unless user came from AUTO and wants manual (not needed)
            if (!TextUtils.isEmpty(source)) editingBill.source = source;
            if (!TextUtils.isEmpty(paymentApp)) editingBill.paymentApp = paymentApp;

            viewModel.update(editingBill);
        } else {
            Bill bill = new Bill(
                    amount,
                    category,
                    TextUtils.isEmpty(note) ? null : note,
                    selectedTimestampMs,
                    receiptUriString,
                    source,
                    paymentApp
            );
            viewModel.insert(bill);
        }

        finish();
    }

    private void onDeleteClicked() {
        if (editingBill == null) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete?")
                .setMessage("Can't be restored after deletion")
                .setPositiveButton(R.string.delete, (d, w) -> {
                    viewModel.delete(editingBill);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
