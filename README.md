# KillBill v2.0.0 --- Living Expenses Accounting APP
This is an improved Java version of the original Kotlin KillBill ledger app.(https://github.com/JerryHuang0424/killbill)

Author: Kaifeng JIN ([00HelloKing](https://github.com/00HelloKing))

Institution: NUIST & SETU

## Key Improvements
-  **Converted to Java**
-  **Room (SQLite) persistence** (stable, fast, no manual JSON parsing)
-  **MVVM (ViewModel + LiveData + Repository)** for cleaner architecture
-  **Receipt photo capture (Camera)** and saved as Uri in each bill
-  **Auto-capture helper**: listens to **Alipay / WeChat** notifications via `NotificationListenerService`
  - Posts a "Tap to record" notification with prefilled amount (safe on modern Android)
-  Swipe-to-delete + Undo
-  Monthly pie chart statistics (MPAndroidChart)
-  CSV export + share

## Permissions
- Camera: for receipt photos
- Notification access: for auto-capture (enable in Settings -> Notification access)
- POST_NOTIFICATIONS (Android 13+): for showing auto-capture prompt notifications
