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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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

        setupBadgeList();
        setupRecyclerView();
        loadAchievements();
    }

    private void setupBadgeList() {
        badgeList.clear();
        badgeList.add(new BadgeItem("first_transaction", "First Steps", "Log your first transaction", R.drawable.ic_check));
        badgeList.add(new BadgeItem("streak_7", "Disciplined", "Keep a 7-day logging streak", R.drawable.ic_calendar));
        badgeList.add(new BadgeItem("streak_30", "Habitual Saver", "Keep a 30-day logging streak", R.drawable.ic_alert));
        badgeList.add(new BadgeItem("saver_1000", "Centurion", "Save ₹1,000 in a single month", R.drawable.ic_money_saving));
        badgeList.add(new BadgeItem("saver_10000", "Wealth Builder", "Save ₹10,000 in a single month", R.drawable.ic_piggy_bank));
        badgeList.add(new BadgeItem("budget_champion", "Budget Master", "Stay within all category budgets for a month", R.drawable.ic_budget));
    }

    private void setupRecyclerView() {
        badgeAdapter = new BadgeAdapter(badgeList);
        binding.rvBadges.setLayoutManager(new GridLayoutManager(getContext(), 3));
        binding.rvBadges.setAdapter(badgeAdapter);
    }

    private void loadAchievements() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null || documentSnapshot == null || !documentSnapshot.exists() || getContext() == null) return;

                    // Load Streak details
                    long currentStreak = 0;
                    if (documentSnapshot.contains("dailyStreak")) {
                        Long val = documentSnapshot.getLong("dailyStreak");
                        if (val != null) currentStreak = val;
                    }

                    long longestStreak = 0;
                    if (documentSnapshot.contains("longestStreak")) {
                        Long val = documentSnapshot.getLong("longestStreak");
                        if (val != null) longestStreak = val;
                    }

                    binding.txtCurrentStreak.setText(currentStreak + " Day Streak");
                    binding.txtLongestStreak.setText("Longest Streak: " + longestStreak + " Days");

                    // Load awarded badges
                    List<String> awardedBadges = (List<String>) documentSnapshot.get("awardedBadges");
                    if (awardedBadges == null) awardedBadges = new ArrayList<>();

                    badgeAdapter.setAwardedBadges(awardedBadges);

                    // Load challenges
                    loadChallenges(uid, currentStreak);
                });
    }

    private void loadChallenges(String uid, long currentStreak) {
        // We will calculate monthly savings dynamically for challenges
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);

        Calendar startCal = Calendar.getInstance();
        startCal.set(year, month, 1, 0, 0, 0);
        Date startDate = startCal.getTime();

        Calendar endCal = Calendar.getInstance();
        endCal.set(year, month, endCal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
        Date endDate = endCal.getTime();

        mFirestore.collection("users").document(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (getContext() == null || binding == null) return;

                    double totalIncome = 0;
                    double totalExpense = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Double amount = doc.getDouble("amount");
                        String type = doc.getString("type");
                        if (amount != null && type != null) {
                            if ("income".equals(type)) {
                                totalIncome += amount;
                            } else if ("expense".equals(type)) {
                                totalExpense += amount;
                            }
                        }
                    }

                    double savings = totalIncome - totalExpense;

                    binding.layoutChallenges.removeAllViews();

                    // Challenge 1: 7-Day Streak
                    addChallengeRow("Week of Discipline", 
                            "Log transactions for 7 consecutive days.", 
                            currentStreak, 7, "Days");

                    // Challenge 2: Monthly Saver (₹1,000)
                    addChallengeRow("Centurion Saver", 
                            "Save at least ₹1,000 this calendar month.", 
                            (long) Math.max(0, savings), 1000, "₹");

                    // Challenge 3: Monthly Saver (₹10,000)
                    addChallengeRow("Wealth Builder", 
                            "Save at least ₹10,000 this calendar month.", 
                            (long) Math.max(0, savings), 10000, "₹");
                });
    }

    private void addChallengeRow(String title, String description, long current, long target, String unit) {
        if (getContext() == null) return;

        ItemChallengeBinding challengeBinding = ItemChallengeBinding.inflate(getLayoutInflater(), binding.layoutChallenges, false);
        challengeBinding.txtChallengeTitle.setText(title);
        challengeBinding.txtChallengeDescription.setText(description);

        if ("₹".equals(unit)) {
            challengeBinding.txtChallengeProgressText.setText("₹" + current + " / ₹" + target);
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
