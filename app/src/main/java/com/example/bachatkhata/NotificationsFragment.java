package com.example.bachatkhata;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentNotificationsBinding;
import com.example.bachatkhata.databinding.ItemNotificationBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    private final List<DocumentSnapshot> notificationDocs = new ArrayList<>();
    private NotificationAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupListeners();
        loadNotifications();
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter();
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvNotifications.setAdapter(adapter);

        // Add swipe to delete functionality
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    deleteNotification(position);
                }
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvNotifications);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.btnMarkAllRead.setOnClickListener(v -> markAllNotificationsAsRead());

        binding.btnClearAll.setOnClickListener(v -> clearAllNotifications());
    }

    private void loadNotifications() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        mFirestore.collection("users").document(uid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;

                    notificationDocs.clear();
                    if (value != null) {
                        notificationDocs.addAll(value.getDocuments());
                    }

                    if (binding != null) {
                        if (notificationDocs.isEmpty()) {
                            binding.layoutEmptyState.setVisibility(View.VISIBLE);
                            binding.rvNotifications.setVisibility(View.GONE);
                            binding.btnMarkAllRead.setEnabled(false);
                            binding.btnClearAll.setEnabled(false);
                        } else {
                            binding.layoutEmptyState.setVisibility(View.GONE);
                            binding.rvNotifications.setVisibility(View.VISIBLE);
                            binding.btnMarkAllRead.setEnabled(true);
                            binding.btnClearAll.setEnabled(true);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    private void markAllNotificationsAsRead() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        WriteBatch batch = mFirestore.batch();
        boolean hasUnread = false;

        for (DocumentSnapshot doc : notificationDocs) {
            Boolean isRead = doc.getBoolean("isRead");
            if (isRead != null && !isRead) {
                batch.update(doc.getReference(), "isRead", true);
                hasUnread = true;
            }
        }

        if (hasUnread) {
            batch.commit().addOnSuccessListener(aVoid -> {
                if (getView() != null) {
                    Snackbar.make(getView(), "All notifications marked as read", Snackbar.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void clearAllNotifications() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        WriteBatch batch = mFirestore.batch();
        for (DocumentSnapshot doc : notificationDocs) {
            batch.delete(doc.getReference());
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            if (getView() != null) {
                Snackbar.make(getView(), "All notifications cleared", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteNotification(int position) {
        DocumentSnapshot doc = notificationDocs.get(position);
        doc.getReference().delete().addOnSuccessListener(aVoid -> {
            if (getView() != null) {
                Snackbar.make(getView(), "Notification deleted", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void markAsRead(DocumentSnapshot doc) {
        Boolean isRead = doc.getBoolean("isRead");
        if (isRead != null && !isRead) {
            doc.getReference().update("isRead", true);
        }
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemNotificationBinding itemBinding = ItemNotificationBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = notificationDocs.get(position);
            holder.bind(doc);
        }

        @Override
        public int getItemCount() {
            return notificationDocs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemNotificationBinding binding;

            public ViewHolder(ItemNotificationBinding binding) {
                super(binding.getRoot());
                this.binding = binding;

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        markAsRead(notificationDocs.get(pos));
                    }
                });
            }

            public void bind(DocumentSnapshot doc) {
                String title = doc.getString("title");
                String message = doc.getString("message");
                String type = doc.getString("type");
                Boolean isRead = doc.getBoolean("isRead");
                Timestamp createdAt = doc.getTimestamp("createdAt");

                binding.txtTitle.setText(title != null ? title : "Notification");
                binding.txtMessage.setText(message != null ? message : "");

                // Relative time span formatting (e.g. "2 hours ago")
                if (createdAt != null) {
                    long now = System.currentTimeMillis();
                    CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                            createdAt.toDate().getTime(), now, DateUtils.MINUTE_IN_MILLIS);
                    binding.txtTime.setText(relativeTime);
                } else {
                    binding.txtTime.setText("");
                }

                // Apply type icon & icon tints
                String icon = "📢";
                String bgHex = "#7C6FE0"; // default primary
                if ("success".equalsIgnoreCase(type)) {
                    icon = "✅";
                    bgHex = "#5DCAA5"; // green
                } else if ("alert".equalsIgnoreCase(type)) {
                    icon = "🚨";
                    bgHex = "#E24B4A"; // red
                } else if ("info".equalsIgnoreCase(type)) {
                    icon = "ℹ️";
                    bgHex = "#7C6FE0"; // primary
                }

                binding.txtNotificationIcon.setText(icon);
                binding.layoutIconFrame.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(bgHex)));

                // Display unread dot indicator
                if (isRead != null && isRead) {
                    binding.viewUnreadDot.setVisibility(View.GONE);
                } else {
                    binding.viewUnreadDot.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
