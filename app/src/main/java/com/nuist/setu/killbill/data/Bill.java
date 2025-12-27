package com.nuist.setu.killbill.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A single expense record.
 * timestamp: Unix time millis.
 * receiptUri: Uri string for a receipt photo captured by camera (optional).
 */
@Entity(tableName = "bills")
public class Bill {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public double amount;

    @NonNull
    public String category;

    @Nullable
    public String note;

    public long timestamp;

    @Nullable
    public String receiptUri;

    /**
     * "MANUAL" or "AUTO"
     */
    @NonNull
    public String source;

    /**
     * "Alipay" / "WeChat" / null
     */
    @Nullable
    public String paymentApp;

    public Bill(double amount,
                @NonNull String category,
                @Nullable String note,
                long timestamp,
                @Nullable String receiptUri,
                @NonNull String source,
                @Nullable String paymentApp) {
        this.amount = amount;
        this.category = category;
        this.note = note;
        this.timestamp = timestamp;
        this.receiptUri = receiptUri;
        this.source = source;
        this.paymentApp = paymentApp;
    }
}
