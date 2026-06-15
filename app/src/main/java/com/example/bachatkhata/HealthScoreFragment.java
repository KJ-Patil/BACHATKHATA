package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.bachatkhata.databinding.FragmentHealthScoreBinding;
import com.example.bachatkhata.databinding.ItemHealthMetricBinding;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HealthScoreFragment extends Fragment {

    private FragmentHealthScoreBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHealthScoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        if (mAuth.getCurrentUser() != null) {
            calculateAndDisplayHealthScore();
        }
    }

    private void calculateAndDisplayHealthScore() {
        String uid = mAuth.getCurrentUser().getUid();

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

        Task<QuerySnapshot> txnTask = mFirestore.collection("users").document(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", earliestDate)
                .get();

        Task<QuerySnapshot> budgetTask = mFirestore.collection("users").document(uid).collection("budgets")
                .whereEqualTo("month", currentMonth)
                .whereEqualTo("year", currentYear)
                .get();

        Task<QuerySnapshot> goalsTask = mFirestore.collection("users").document(uid).collection("savings_goals")
                .get();

        Task<QuerySnapshot> emisTask = mFirestore.collection("users").document(uid).collection("emis")
                .get();

        Tasks.whenAllComplete(txnTask, budgetTask, goalsTask, emisTask)
                .addOnCompleteListener(task -> {
                    if (binding == null) return;
                    if (!task.isSuccessful()) {
                        binding.txtHealthRating.setText("Failed to calculate score");
                        return;
                    }

                    double totalIncome = 0.0;
                    double totalExpense = 0.0;
                    Map<String, Double> categoryExpenses = new HashMap<>();

                    double w1 = 0, w2 = 0, w3 = 0, w4 = 0;
                    long nowMs = new Date().getTime();

                    QuerySnapshot txnSnap = (QuerySnapshot) txnTask.getResult();
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
                    QuerySnapshot budgetSnap = (QuerySnapshot) budgetTask.getResult();
                    int totalBudgets = 0;
                    if (budgetSnap != null) {
                        totalBudgets = budgetSnap.getDocuments().size();
                    }

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
                    QuerySnapshot goalSnap = (QuerySnapshot) goalsTask.getResult();
                    int totalGoals = 0;
                    if (goalSnap != null) {
                        totalGoals = goalSnap.getDocuments().size();
                    }

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
                    QuerySnapshot emiSnap = (QuerySnapshot) emisTask.getResult();
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

                    // Save score in Firestore
                    mFirestore.collection("users").document(uid).update("healthScore", score);

                    // Update main UI gauge
                    binding.healthScoreGauge.setCenterLabel("HEALTH");
                    binding.healthScoreGauge.setScore(score, 0, 100);

                    String rating;
                    int ratingColor;
                    if (score >= 80) {
                        rating = "Excellent Health";
                        ratingColor = Color.parseColor("#5DCAA5");
                    } else if (score >= 50) {
                        rating = "Good Health";
                        ratingColor = Color.parseColor("#EF9F27");
                    } else {
                        rating = "Critical Health Alert";
                        ratingColor = Color.parseColor("#E24B4A");
                    }
                    binding.txtHealthRating.setText(rating);
                    binding.txtHealthRating.setTextColor(ratingColor);

                    SimpleDateFormat displayFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
                    binding.txtLastUpdated.setText("Last updated: " + displayFormat.format(new Date()));

                    // Add individual metrics
                    binding.layoutMetrics.removeAllViews();
                    addMetricRow("Savings Rate", (int) Math.round(savingsRateScore), 30, "Tip: Save at least 20% of your total income monthly.", 15, 24);
                    addMetricRow("Budget Adherence", (int) Math.round(budgetAdherenceScore), 25, "Tip: Create budgets and track expenses to stay within limits.", 12, 20);
                    addMetricRow("Goal Progress", (int) Math.round(goalProgressScore), 20, "Tip: Contribute regularly to savings goals to keep compound progress.", 10, 16);
                    addMetricRow("Debt Ratio (EMI)", (int) Math.round(debtRatioScore), 15, "Tip: Keep EMI burden below 15% of your regular income.", 7, 12);
                    addMetricRow("Expense Consistency", (int) Math.round(consistencyScore), 10, "Tip: Avoid large unpredicted expense spikes week-over-week.", 5, 8);

                    // Recommendations section
                    List<String> tipsList = new ArrayList<>();
                    // Rank lowest scoring relative components
                    double srPct = savingsRateScore / 30.0;
                    double baPct = budgetAdherenceScore / 25.0;
                    double gpPct = goalProgressScore / 20.0;
                    double drPct = debtRatioScore / 15.0;
                    double ecPct = consistencyScore / 10.0;

                    // Sort indexes or find lowest
                    double[] percentages = {srPct, baPct, gpPct, drPct, ecPct};
                    String[] labels = {
                            "Savings Rate: Save at least 20% of your income. Automate contributions or enable Round-Ups.",
                            "Budget Adherence: Set up category limits and monitor your spending daily to prevent overruns.",
                            "Savings Goals: Set clear targets and make small recurring payments to grow your virtual jar.",
                            "Debt Ratio: Keep EMI burden low. Avoid new loans and pay down high-interest liabilities.",
                            "Expense Consistency: Plan your major purchases instead of letting expenses spike randomly."
                    };

                    // Find two lowest percentages
                    int lowestIdx1 = -1;
                    int lowestIdx2 = -1;
                    double min1 = 999.0;
                    double min2 = 999.0;

                    for (int i = 0; i < percentages.length; i++) {
                        if (percentages[i] < min1) {
                            min2 = min1;
                            lowestIdx2 = lowestIdx1;
                            min1 = percentages[i];
                            lowestIdx1 = i;
                        } else if (percentages[i] < min2) {
                            min2 = percentages[i];
                            lowestIdx2 = i;
                        }
                    }

                    if (lowestIdx1 != -1 && min1 < 1.0) {
                        tipsList.add("• " + labels[lowestIdx1]);
                    }
                    if (lowestIdx2 != -1 && min2 < 1.0) {
                        tipsList.add("• " + labels[lowestIdx2]);
                    }

                    if (!tipsList.isEmpty()) {
                        binding.cardImprovement.setVisibility(View.VISIBLE);
                        StringBuilder sb = new StringBuilder();
                        for (String tip : tipsList) {
                            if (sb.length() > 0) sb.append("\n\n");
                            sb.append(tip);
                        }
                        binding.txtImprovementTips.setText(sb.toString());
                    } else {
                        binding.cardImprovement.setVisibility(View.GONE);
                    }
                });
    }

    private void addMetricRow(String name, int score, int maxScore, String tip, int amberThresh, int greenThresh) {
        ItemHealthMetricBinding itemBinding = ItemHealthMetricBinding.inflate(getLayoutInflater(), binding.layoutMetrics, false);
        itemBinding.txtMetricName.setText(name);
        itemBinding.txtMetricScore.setText(score + " / " + maxScore + " pts");
        
        int pct = maxScore > 0 ? (score * 100) / maxScore : 0;
        itemBinding.pbMetricProgress.setProgress(pct);

        int color;
        if (score >= greenThresh) {
            color = Color.parseColor("#5DCAA5"); // Green
        } else if (score >= amberThresh) {
            color = Color.parseColor("#EF9F27"); // Amber
        } else {
            color = Color.parseColor("#E24B4A"); // Red
        }

        itemBinding.pbMetricProgress.setProgressTintList(ColorStateList.valueOf(color));
        itemBinding.txtMetricScore.setTextColor(color);
        itemBinding.txtMetricTip.setText(tip);

        binding.layoutMetrics.addView(itemBinding.getRoot());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
