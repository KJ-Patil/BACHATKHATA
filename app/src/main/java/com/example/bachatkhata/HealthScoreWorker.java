package com.example.bachatkhata;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HealthScoreWorker extends Worker {

    private final FirebaseFirestore mFirestore;

    public HealthScoreWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mFirestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return Result.success();
        }

        String uid = auth.getCurrentUser().getUid();

        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfMonth = cal.getTime();

            Calendar cal30 = Calendar.getInstance();
            cal30.add(Calendar.DAY_OF_YEAR, -30);
            Date startOf30Days = cal30.getTime();

            Date earliestDate = startOfMonth.before(startOf30Days) ? startOfMonth : startOf30Days;

            int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);

            QuerySnapshot txnSnap = Tasks.await(mFirestore.collection("users").document(uid).collection("transactions")
                    .whereGreaterThanOrEqualTo("date", earliestDate)
                    .get());

            QuerySnapshot budgetSnap = Tasks.await(mFirestore.collection("users").document(uid).collection("budgets")
                    .whereEqualTo("month", currentMonth)
                    .whereEqualTo("year", currentYear)
                    .get());

            QuerySnapshot goalSnap = Tasks.await(mFirestore.collection("users").document(uid).collection("savings_goals")
                    .get());

            QuerySnapshot emiSnap = Tasks.await(mFirestore.collection("users").document(uid).collection("emis")
                    .get());

            double totalIncome = 0.0;
            double totalExpense = 0.0;
            Map<String, Double> categoryExpenses = new HashMap<>();

            double w1 = 0, w2 = 0, w3 = 0, w4 = 0;
            long nowMs = new Date().getTime();

            if (txnSnap != null) {
                for (DocumentSnapshot doc : txnSnap.getDocuments()) {
                    Transaction t = Transaction.fromDocument(doc);
                    if (t.getDate() == null) continue;

                    double amt = t.getAmount();
                    String type = t.getType();

                    // Month boundaries
                    if (t.getDate().after(startOfMonth) || t.getDate().equals(startOfMonth)) {
                        if ("income".equalsIgnoreCase(type)) {
                            totalIncome += amt;
                        } else if ("expense".equalsIgnoreCase(type)) {
                            totalExpense += amt;
                            String cat = t.getCategory();
                            if (cat != null) {
                                categoryExpenses.put(cat, categoryExpenses.getOrDefault(cat, 0.0) + amt);
                            }
                        }
                    }

                    // 30 days boundaries for weekly consistency
                    if (t.getDate().after(startOf30Days) || t.getDate().equals(startOf30Days)) {
                        if ("expense".equalsIgnoreCase(type)) {
                            long diffMs = nowMs - t.getDate().getTime();
                            long diffDays = TimeUnit.MILLISECONDS.toDays(diffMs);
                            if (diffDays <= 7) {
                                w1 += amt;
                            } else if (diffDays <= 14) {
                                w2 += amt;
                            } else if (diffDays <= 21) {
                                w3 += amt;
                            } else if (diffDays <= 30) {
                                w4 += amt;
                            }
                        }
                    }
                }
            }

            // Consistency SD / mean calculation
            double mean = (w1 + w2 + w3 + w4) / 4.0;
            double variance = (Math.pow(w1 - mean, 2) + Math.pow(w2 - mean, 2) + Math.pow(w3 - mean, 2) + Math.pow(w4 - mean, 2)) / 4.0;
            double sd = Math.sqrt(variance);
            double cv = mean > 0 ? sd / mean : 0.0;

            // 1. Savings Rate (max 30)
            double savingsRateScore = 0.0;
            if (totalIncome > 0) {
                double rate = (totalIncome - totalExpense) / totalIncome;
                if (rate >= 0.20) {
                    savingsRateScore = 30.0;
                } else if (rate > 0) {
                    savingsRateScore = (rate / 0.20) * 30.0;
                }
            } else {
                savingsRateScore = (totalExpense == 0) ? 15.0 : 0.0;
            }

            // 2. Budget Adherence (max 25)
            double budgetAdherenceScore = 0.0;
            int totalBudgets = budgetSnap != null ? budgetSnap.getDocuments().size() : 0;

            if (totalBudgets > 0) {
                int numUnderBudget = 0;
                for (DocumentSnapshot doc : budgetSnap.getDocuments()) {
                    Budget b = Budget.fromDocument(doc);
                    double spent = categoryExpenses.getOrDefault(b.getCategory(), 0.0);
                    if (spent <= b.getLimitAmount()) {
                        numUnderBudget++;
                    }
                }
                budgetAdherenceScore = ((double) numUnderBudget / totalBudgets) * 25.0;
            } else {
                if (totalIncome > 0) {
                    if (totalExpense <= 0.8 * totalIncome) {
                        budgetAdherenceScore = 25.0;
                    } else {
                        double ratio = (totalExpense - 0.8 * totalIncome) / (0.8 * totalIncome);
                        budgetAdherenceScore = Math.max(0.0, 25.0 * (1.0 - ratio));
                    }
                } else {
                    budgetAdherenceScore = (totalExpense == 0) ? 25.0 : 0.0;
                }
            }

            // 3. Goal Progress (max 20)
            double goalProgressScore = 0.0;
            int totalGoals = goalSnap != null ? goalSnap.getDocuments().size() : 0;

            if (totalGoals > 0) {
                double sumProgress = 0.0;
                for (DocumentSnapshot doc : goalSnap.getDocuments()) {
                    SavingsGoal g = SavingsGoal.fromDocument(doc);
                    if (g.getTargetAmount() > 0) {
                        double prog = g.getSavedAmount() / g.getTargetAmount();
                        sumProgress += Math.min(1.0, Math.max(0.0, prog));
                    } else {
                        sumProgress += 1.0;
                    }
                }
                goalProgressScore = (sumProgress / totalGoals) * 20.0;
            } else {
                goalProgressScore = 10.0;
            }

            // 4. Debt Ratio (max 15)
            double debtRatioScore = 0.0;
            double totalMonthlyEmi = 0.0;
            if (emiSnap != null) {
                for (DocumentSnapshot doc : emiSnap.getDocuments()) {
                    Double emiVal = doc.getDouble("emiAmount");
                    if (emiVal != null) {
                        totalMonthlyEmi += emiVal;
                    }
                }
            }

            if (totalMonthlyEmi == 0) {
                debtRatioScore = 15.0;
            } else {
                if (totalIncome > 0) {
                    double debtRatio = totalMonthlyEmi / totalIncome;
                    if (debtRatio <= 0.15) {
                        debtRatioScore = 15.0;
                    } else if (debtRatio >= 0.50) {
                        debtRatioScore = 0.0;
                    } else {
                        debtRatioScore = 15.0 * (1.0 - (debtRatio - 0.15) / 0.35);
                    }
                } else {
                    debtRatioScore = 0.0;
                }
            }

            // 5. Expense Consistency (max 10)
            double consistencyScore = 0.0;
            if (mean == 0.0) {
                consistencyScore = 10.0;
            } else {
                if (cv <= 0.2) {
                    consistencyScore = 10.0;
                } else if (cv >= 1.0) {
                    consistencyScore = 0.0;
                } else {
                    consistencyScore = 10.0 * (1.0 - (cv - 0.2) / 0.8);
                }
            }

            int score = (int) Math.round(savingsRateScore + budgetAdherenceScore + goalProgressScore + debtRatioScore + consistencyScore);
            score = Math.max(0, Math.min(100, score));

            // Sync with Firestore
            Tasks.await(mFirestore.collection("users").document(uid).update("healthScore", score));

            // Trigger weekly report notification
            triggerNotification(
                    "Weekly Financial Health Update",
                    "Your weekly health report is ready! Current Score: " + score + "/100.",
                    uid
            );

            return Result.success();

        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private void triggerNotification(String title, String message, String uid) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "health_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Health Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Weekly financial health score analysis report.");
            notificationManager.createNotificationChannel(channel);
        }

        android.content.Intent intent = new android.content.Intent(getApplicationContext(), MainActivity.class);
        intent.putExtra("destination", "health");
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                getApplicationContext(),
                (int) System.currentTimeMillis(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());

        // Save inside Firestore user notifications inbox
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", "health");
        notification.put("isRead", false);
        notification.put("createdAt", Timestamp.now());

        mFirestore.collection("users").document(uid).collection("notifications").add(notification);
    }
}
