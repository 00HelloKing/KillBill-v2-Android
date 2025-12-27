package com.nuist.setu.killbill.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository (single source of truth for data operations).
 * - UI observes LiveData from Room directly.
 * - Writes run on a background thread (ExecutorService).
 */
public class BillRepository {

    public interface ResultCallback<T> {
        void onResult(T result);
    }

    private static volatile BillRepository INSTANCE;

    private final BillDao billDao;
    private final ExecutorService ioExecutor;
    private final Handler mainHandler;

    private BillRepository(Context context) {
        this.billDao = AppDatabase.getInstance(context).billDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static BillRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (BillRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new BillRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<Bill>> getAllBills() {
        return billDao.getAllBills();
    }

    public LiveData<List<Bill>> getBillsBetween(long start, long endExclusive) {
        return billDao.getBillsBetween(start, endExclusive);
    }

    public LiveData<List<CategoryTotal>> getCategoryTotalsBetween(long start, long endExclusive) {
        return billDao.getCategoryTotalsBetween(start, endExclusive);
    }

    public LiveData<Bill> getBillById(long id) {
        return billDao.getBillById(id);
    }

    public void insert(Bill bill) {
        ioExecutor.execute(() -> billDao.insert(bill));
    }

    public void update(Bill bill) {
        ioExecutor.execute(() -> billDao.update(bill));
    }

    public void delete(Bill bill) {
        ioExecutor.execute(() -> billDao.delete(bill));
    }

    /**
     * For CSV export or other one-shot operations.
     */
    public void getAllBillsOnce(ResultCallback<List<Bill>> callback) {
        ioExecutor.execute(() -> {
            List<Bill> list = billDao.getAllBillsOnce();
            mainHandler.post(() -> callback.onResult(list));
        });
    }
}
