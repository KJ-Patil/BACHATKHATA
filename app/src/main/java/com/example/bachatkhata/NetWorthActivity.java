package com.example.bachatkhata;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.ActivityNetWorthBinding;
import com.example.bachatkhata.databinding.ItemAssetBinding;
import com.example.bachatkhata.databinding.ItemLiabilityBinding;
import com.github.mikephil.charting.data.Entry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NetWorthActivity extends BaseActivity {

    private ActivityNetWorthBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private final List<DocumentSnapshot> assetsList = new ArrayList<>();
    private final List<DocumentSnapshot> liabilitiesList = new ArrayList<>();

    private AssetAdapter assetAdapter;
    private LiabilityAdapter liabilityAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNetWorthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupViews();
        setupRecyclerViews();
        setupSwipeToDelete();

        if (mAuth.getCurrentUser() != null) {
            refreshData();
        }
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnAddAsset.setOnClickListener(v -> {
            AddAssetBottomSheet bottomSheet = new AddAssetBottomSheet();
            bottomSheet.setOnAssetAddedListener(this::refreshData);
            bottomSheet.show(getSupportFragmentManager(), "AddAssetBottomSheet");
        });

        binding.btnAddLiability.setOnClickListener(v -> {
            AddLiabilityBottomSheet bottomSheet = new AddLiabilityBottomSheet();
            bottomSheet.setOnLiabilityAddedListener(this::refreshData);
            bottomSheet.show(getSupportFragmentManager(), "AddLiabilityBottomSheet");
        });
    }

    private void setupRecyclerViews() {
        assetAdapter = new AssetAdapter();
        binding.rvAssets.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAssets.setAdapter(assetAdapter);

        liabilityAdapter = new LiabilityAdapter();
        binding.rvLiabilities.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLiabilities.setAdapter(liabilityAdapter);
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback assetSwipe = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                deleteAsset(position);
            }
        };
        new ItemTouchHelper(assetSwipe).attachToRecyclerView(binding.rvAssets);

        ItemTouchHelper.SimpleCallback liabilitySwipe = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                deleteLiability(position);
            }
        };
        new ItemTouchHelper(liabilitySwipe).attachToRecyclerView(binding.rvLiabilities);
    }

    private void refreshData() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        Task<QuerySnapshot> getAssets = mFirestore.collection("users").document(uid).collection("assets").get();
        Task<QuerySnapshot> getLiabilities = mFirestore.collection("users").document(uid).collection("liabilities").get();

        Tasks.whenAllComplete(getAssets, getLiabilities)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) return;

                    assetsList.clear();
                    liabilitiesList.clear();

                    double totalAssets = 0.0;
                    double totalLiabilities = 0.0;

                    QuerySnapshot assetsSnap = (QuerySnapshot) getAssets.getResult();
                    if (assetsSnap != null) {
                        for (DocumentSnapshot doc : assetsSnap.getDocuments()) {
                            assetsList.add(doc);
                            Double val = doc.getDouble("amount");
                            if (val != null) {
                                totalAssets += val;
                            }
                        }
                    }

                    QuerySnapshot liabilitiesSnap = (QuerySnapshot) getLiabilities.getResult();
                    if (liabilitiesSnap != null) {
                        for (DocumentSnapshot doc : liabilitiesSnap.getDocuments()) {
                            liabilitiesList.add(doc);
                            Double val = doc.getDouble("amount");
                            if (val != null) {
                                totalLiabilities += val;
                            }
                        }
                    }

                    double netWorth = totalAssets - totalLiabilities;

                    // Update UI text values
                    binding.txtTotalAssets.setText(CurrencyManager.getInstance().formatAmount(totalAssets));
                    binding.txtTotalLiabilities.setText(CurrencyManager.getInstance().formatAmount(totalLiabilities));
                    binding.txtNetWorthValue.setText(CurrencyManager.getInstance().formatAmount(netWorth));

                    assetAdapter.notifyDataSetChanged();
                    liabilityAdapter.notifyDataSetChanged();

                    // Save snapshot for current month
                    saveMonthlySnapshot(netWorth, totalAssets, totalLiabilities);

                    // Load trends and render chart
                    loadTrendsAndChart();
                });
    }

    private void saveMonthlySnapshot(double netWorth, double totalAssets, double totalLiabilities) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH); // 0-indexed
        String docId = String.format(Locale.US, "%04d_%02d", year, month + 1);

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("year", year);
        snapshot.put("month", month);
        snapshot.put("netWorth", netWorth);
        snapshot.put("totalAssets", totalAssets);
        snapshot.put("totalLiabilities", totalLiabilities);
        snapshot.put("timestamp", Timestamp.now());

        mFirestore.collection("users").document(uid)
                .collection("net_worth_snapshots").document(docId)
                .set(snapshot);
    }

    private void loadTrendsAndChart() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .collection("net_worth_snapshots")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(12)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Entry> entries = new ArrayList<>();
                    int index = 0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Double val = doc.getDouble("netWorth");
                        if (val != null) {
                            entries.add(new Entry(index++, val.floatValue()));
                        }
                    }

                    if (!entries.isEmpty()) {
                        ChartStyler.applyLineChartStyle(NetWorthActivity.this, binding.chartNetWorthTrend, entries);
                    }
                });
    }

    private void deleteAsset(int position) {
        if (mAuth.getCurrentUser() == null || position >= assetsList.size()) return;
        String uid = mAuth.getCurrentUser().getUid();
        DocumentSnapshot doc = assetsList.get(position);
        String assetId = doc.getId();

        mFirestore.collection("users").document(uid).collection("assets").document(assetId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(NetWorthActivity.this, "Asset deleted", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(NetWorthActivity.this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    assetAdapter.notifyItemChanged(position);
                });
    }

    private void deleteLiability(int position) {
        if (mAuth.getCurrentUser() == null || position >= liabilitiesList.size()) return;
        String uid = mAuth.getCurrentUser().getUid();
        DocumentSnapshot doc = liabilitiesList.get(position);
        String liabilityId = doc.getId();

        mFirestore.collection("users").document(uid).collection("liabilities").document(liabilityId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(NetWorthActivity.this, "Liability deleted", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(NetWorthActivity.this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    liabilityAdapter.notifyItemChanged(position);
                });
    }

    // Asset RecyclerView Adapter
    private class AssetAdapter extends RecyclerView.Adapter<AssetViewHolder> {
        @NonNull
        @Override
        public AssetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAssetBinding itemBinding = ItemAssetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new AssetViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull AssetViewHolder holder, int position) {
            DocumentSnapshot doc = assetsList.get(position);
            String name = doc.getString("name");
            String type = doc.getString("type");
            Double amount = doc.getDouble("amount");

            holder.binding.txtAssetName.setText(name != null ? name : "Asset");
            holder.binding.txtAssetType.setText(type != null ? type : "Other");
            holder.binding.txtAssetValue.setText(CurrencyManager.getInstance().formatAmount(amount != null ? amount : 0.0));

            // Emoji selector based on type
            String emoji = "💰";
            if (type != null) {
                switch (type.toLowerCase(Locale.US)) {
                    case "cash":
                        emoji = "💵";
                        break;
                    case "savings account":
                        emoji = "🏦";
                        break;
                    case "fixed deposit":
                        emoji = "📜";
                        break;
                    case "gold":
                        emoji = "🪙";
                        break;
                    case "mutual funds":
                    case "stocks":
                        emoji = "📈";
                        break;
                    case "real estate":
                        emoji = "🏡";
                        break;
                    case "vehicles":
                        emoji = "🚗";
                        break;
                }
            }
            holder.binding.txtAssetEmoji.setText(emoji);
        }

        @Override
        public int getItemCount() {
            return assetsList.size();
        }
    }

    private static class AssetViewHolder extends RecyclerView.ViewHolder {
        final ItemAssetBinding binding;

        AssetViewHolder(ItemAssetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    // Liability RecyclerView Adapter
    private class LiabilityAdapter extends RecyclerView.Adapter<LiabilityViewHolder> {
        @NonNull
        @Override
        public LiabilityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemLiabilityBinding itemBinding = ItemLiabilityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new LiabilityViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull LiabilityViewHolder holder, int position) {
            DocumentSnapshot doc = liabilitiesList.get(position);
            String name = doc.getString("name");
            String type = doc.getString("type");
            Double amount = doc.getDouble("amount");

            holder.binding.txtLiabilityName.setText(name != null ? name : "Liability");
            holder.binding.txtLiabilityType.setText(type != null ? type : "Other");
            holder.binding.txtLiabilityValue.setText(CurrencyManager.getInstance().formatAmount(amount != null ? amount : 0.0));

            // Emoji based on type
            String emoji = "💳";
            if (type != null) {
                switch (type.toLowerCase(Locale.US)) {
                    case "credit card debt":
                        emoji = "💳";
                        break;
                    case "personal loan":
                        emoji = "💵";
                        break;
                    case "home loan":
                        emoji = "🏠";
                        break;
                    case "car loan":
                        emoji = "🚗";
                        break;
                    case "education loan":
                        emoji = "🎓";
                        break;
                    case "outstanding bills":
                        emoji = "📄";
                        break;
                }
            }
            holder.binding.txtLiabilityEmoji.setText(emoji);
        }

        @Override
        public int getItemCount() {
            return liabilitiesList.size();
        }
    }

    private static class LiabilityViewHolder extends RecyclerView.ViewHolder {
        final ItemLiabilityBinding binding;

        LiabilityViewHolder(ItemLiabilityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
