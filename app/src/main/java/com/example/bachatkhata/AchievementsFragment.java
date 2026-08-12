package com.example.bachatkhata;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bachatkhata.databinding.FragmentAchievementsBinding;
import com.example.bachatkhata.databinding.ItemBadgeBinding;
import com.example.bachatkhata.databinding.ItemChallengeBinding;
import com.example.bachatkhata.domain.Streaks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AchievementsFragment extends Fragment {

    private FragmentAchievementsBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private BadgeAdapter badgeAdapter;
    private List<BadgeItem> badgeList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAchievementsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        setupRecyclerView();
        loadAchievements();
    }

    private void setupRecyclerView() {
        badgeAdapter = new BadgeAdapter(badgeList);
        binding.rvBadges.setLayoutManager(new GridLayoutManager(getContext(), 3));
        binding.rvBadges.setAdapter(badgeAdapter);
    }

    /** Icon for a badge id. Falls back to a generic mark for anything unmapped. */
    private static int iconFor(String badgeId) {
        switch (badgeId) {
            case "first_transaction": return R.drawable.ic_check;
            case "streak_3":
            case "streak_7": return R.drawable.ic_calendar;
            case "streak_30": return R.drawable.ic_alert;
            case "saver_20": return R.drawable.ic_money_saving;
            case "saver_40": return R.drawable.ic_piggy_bank;
            case "budget_champion": return R.drawable.ic_budget;
            case "goal_crusher": return R.drawable.ic_piggy_bank;
            case "well_rounded": return R.drawable.ic_budget;
            case "centurion": return R.drawable.ic_money_saving;
            default: return R.drawable.ic_check;
        }
    }

    /**
     * Loads everything the badge engine needs and recomputes from scratch.
     *
     * <p>Streaks and badges are derived, not read from stored flags. A stored award
     * cannot show progress toward the badges still locked, and it drifts once
     * transactions are edited or deleted — the number on screen would then disagree
     * with the ledger it claims to describe.
     */
    private void loadAchievements() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        com.google.firebase.firestore.CollectionReference userRoot =
                mFirestore.collection("users").document(uid).collection("transactions");

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> txnTask =
                userRoot.get();
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> budgetTask =
                mFirestore.collection("users").document(uid).collection("budgets").get();
        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> goalTask =
                mFirestore.collection("users").document(uid).collection("savings_goals").get();

        com.google.android.gms.tasks.Tasks.whenAllSuccess(txnTask, budgetTask, goalTask)
                .addOnSuccessListener(results -> {
                    if (getContext() == null || binding == null) return;
                    render(Streaks.compute(
                            toEntries(txnTask.getResult()),
                            toBudgets(budgetTask.getResult()),
                            toGoalProgress(goalTask.getResult()),
                            LocalDate.now()));
                })
                .addOnFailureListener(e -> {
                    if (getContext() == null || binding == null) return;
                    Toast.makeText(getContext(),
                            "Could not load achievements: " + e.getLocalizedMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private List<Streaks.Entry> toEntries(QuerySnapshot snapshot) {
        List<Streaks.Entry> entries = new ArrayList<>();
        if (snapshot == null) return entries;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Double amount = doc.getDouble("amount");
            String type = doc.getString("type");
            Timestamp date = doc.getTimestamp("date");
            if (amount == null || type == null || date == null) continue;

            LocalDate day = date.toDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            entries.add(new Streaks.Entry(
                    day, amount, "income".equals(type), doc.getString("category")));
        }
        return entries;
    }

    private Map<String, Double> toBudgets(QuerySnapshot snapshot) {
        Map<String, Double> budgets = new HashMap<>();
        if (snapshot == null) return budgets;

        // Budget Boss is a this-month claim, so only this month's limits count.
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH);
        int year = now.get(Calendar.YEAR);

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String category = doc.getString("category");
            Double limit = doc.getDouble("limitAmount");
            Long docMonth = doc.getLong("month");
            Long docYear = doc.getLong("year");
            if (category == null || limit == null) continue;
            if (docMonth != null && docMonth != month) continue;
            if (docYear != null && docYear != year) continue;
            budgets.put(category, limit);
        }
        return budgets;
    }

    private List<Double> toGoalProgress(QuerySnapshot snapshot) {
        List<Double> progress = new ArrayList<>();
        if (snapshot == null) return progress;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Double target = doc.getDouble("targetAmount");
            Double saved = doc.getDouble("savedAmount");
            if (target == null || target <= 0) continue;
            progress.add((saved == null ? 0 : saved) / target);
        }
        return progress;
    }

    private void render(Streaks.Result result) {
        binding.txtCurrentStreak.setText(result.currentStreak + " Day Streak");
        binding.txtLongestStreak.setText("Longest Streak: " + result.longestStreak + " Days");
        binding.txtActiveDays.setText(getString(R.string.streaks_active_days,
                result.activeDays, result.earnedCount, result.badges.size()));

        badgeList.clear();
        List<String> earned = new ArrayList<>();
        for (Streaks.Badge badge : result.badges) {
            badgeList.add(new BadgeItem(badge.id, badge.name, badge.description, iconFor(badge.id)));
            if (badge.earned) earned.add(badge.id);
        }
        badgeAdapter.setAwardedBadges(earned);

        // Challenges: the three closest badges the user has not earned yet, so the
        // list stays useful instead of showing goals already met.
        binding.layoutChallenges.removeAllViews();
        List<Streaks.Badge> pending = new ArrayList<>();
        for (Streaks.Badge badge : result.badges) {
            if (!badge.earned) pending.add(badge);
        }
        Collections.sort(pending, (a, b) -> Integer.compare(b.percent(), a.percent()));

        if (pending.isEmpty()) {
            addChallengeRow("All badges earned",
                    "You have unlocked every achievement. Keep logging to hold your streak.",
                    1, 1, "");
        } else {
            for (int i = 0; i < Math.min(3, pending.size()); i++) {
                Streaks.Badge badge = pending.get(i);
                addChallengeRow(badge.name, badge.description,
                        badge.progress, badge.target, "");
            }
        }
    }

    private void addChallengeRow(String title, String description, long current, long target, String unit) {
        if (getContext() == null) return;

        ItemChallengeBinding challengeBinding = ItemChallengeBinding.inflate(getLayoutInflater(), binding.layoutChallenges, false);
        challengeBinding.txtChallengeTitle.setText(title);
        challengeBinding.txtChallengeDescription.setText(description);

        if (unit == null || unit.isEmpty()) {
            challengeBinding.txtChallengeProgressText.setText(current + " / " + target);
        } else if ("₹".equals(unit)) {
            // A money-denominated challenge. Both figures are rolled up from stored
            // transactions, so they format through the active currency rather than a
            // fixed rupee sign.
            CurrencyManager currency = CurrencyManager.getInstance();
            challengeBinding.txtChallengeProgressText.setText(
                    currency.formatAmount(current) + " / " + currency.formatAmount(target));
        } else {
            challengeBinding.txtChallengeProgressText.setText(current + " / " + target + " " + unit);
        }

        int percent = (int) (((double) current / target) * 100);
        percent = Math.min(100, Math.max(0, percent));

        challengeBinding.pbChallengeProgress.setProgress(percent);
        challengeBinding.txtChallengePercent.setText(percent + "%");

        binding.layoutChallenges.addView(challengeBinding.getRoot());
    }

    private static class BadgeItem {
        String id;
        String name;
        String description;
        int iconRes;

        BadgeItem(String id, String name, String description, int iconRes) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.iconRes = iconRes;
        }
    }

    private class BadgeAdapter extends RecyclerView.Adapter<BadgeViewHolder> {

        private final List<BadgeItem> list;
        private List<String> awardedBadges = new ArrayList<>();

        BadgeAdapter(List<BadgeItem> list) {
            this.list = list;
        }

        void setAwardedBadges(List<String> awarded) {
            this.awardedBadges = awarded;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BadgeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemBadgeBinding b = ItemBadgeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new BadgeViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull BadgeViewHolder holder, int position) {
            BadgeItem item = list.get(position);
            holder.binding.txtBadgeName.setText(item.name);
            holder.binding.txtBadgeDescription.setText(item.description);
            holder.binding.imgBadgeIcon.setImageResource(item.iconRes);

            boolean isUnlocked = awardedBadges.contains(item.id);
            if (isUnlocked) {
                holder.binding.layoutBadgeIconContainer.setAlpha(1.0f);
                holder.binding.txtBadgeName.setAlpha(1.0f);
                holder.binding.txtBadgeDescription.setAlpha(1.0f);
                holder.binding.imgLockOverlay.setVisibility(View.GONE);
                holder.binding.layoutBadgeIconContainer.setBackgroundTintList(
                        ContextCompat.getColorStateList(getContext(), R.color.colorPrimary)
                );
                holder.binding.imgBadgeIcon.setImageTintList(
                        ContextCompat.getColorStateList(getContext(), R.color.colorSurface)
                );
            } else {
                holder.binding.layoutBadgeIconContainer.setAlpha(0.4f);
                holder.binding.txtBadgeName.setAlpha(0.6f);
                holder.binding.txtBadgeDescription.setAlpha(0.6f);
                holder.binding.imgLockOverlay.setVisibility(View.VISIBLE);
                holder.binding.layoutBadgeIconContainer.setBackgroundTintList(
                        ContextCompat.getColorStateList(getContext(), R.color.colorCardBorder)
                );
                holder.binding.imgBadgeIcon.setImageTintList(
                        ContextCompat.getColorStateList(getContext(), R.color.colorTextSecondary)
                );
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    private static class BadgeViewHolder extends RecyclerView.ViewHolder {
        ItemBadgeBinding binding;

        BadgeViewHolder(ItemBadgeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
