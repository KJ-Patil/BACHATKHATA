package com.example.bachatkhata;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentFamilyWalletBinding;
import com.example.bachatkhata.databinding.ItemGroupBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FamilyWalletFragment extends Fragment {

    private FragmentFamilyWalletBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private ListenerRegistration groupsListener;

    private final List<DocumentSnapshot> groupsList = new ArrayList<>();
    private GroupAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFamilyWalletBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupUI();
        observeGroups();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GroupAdapter();
        binding.rvGroups.setAdapter(adapter);

        binding.fabCreateGroup.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), CreateGroupActivity.class));
        });

        binding.btnJoinGroup.setOnClickListener(v -> {
            String inviteCode = binding.etInviteCode.getText().toString().trim();
            if (inviteCode.length() != 6) {
                Toast.makeText(getContext(), "Invite code must be 6 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            joinGroupWithInviteCode(inviteCode);
        });
    }

    private void observeGroups() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        if (groupsListener != null) {
            groupsListener.remove();
        }

        // Real-time listener on groups where current user is a member
        groupsListener = mFirestore.collection("groups")
                .whereArrayContains("memberUids", uid)
                .addSnapshotListener((value, error) -> {
                    if (error != null || binding == null) return;

                    groupsList.clear();
                    if (value != null) {
                        groupsList.addAll(value.getDocuments());
                    }

                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
    }

    private void updateEmptyState() {
        if (groupsList.isEmpty()) {
            binding.rvGroups.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.rvGroups.setVisibility(View.VISIBLE);
            binding.layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void joinGroupWithInviteCode(String inviteCode) {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // 1. Fetch current user profile name first
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String joinerName = "Member";
                    if (userDoc.exists()) {
                        String name = userDoc.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            joinerName = name;
                        }
                    }

                    final String finalJoinerName = joinerName;

                    // 2. Resolve the code through the single-document lookup. This used to
                    //    be a whereEqualTo("inviteCode", ...) query over `groups`, which is
                    //    a list operation the security rules deny — allowing it would let
                    //    anyone enumerate every group in the app. A code you already know
                    //    resolves in one read; the collection stays unwalkable.
                    mFirestore.collection("inviteCodes").document(inviteCode)
                            .get()
                            .addOnSuccessListener(codeDoc -> {
                                String groupId = codeDoc.exists() ? codeDoc.getString("groupId") : null;
                                if (groupId == null || groupId.isEmpty()) {
                                    Toast.makeText(getContext(), "Invalid invite code. Group not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Membership is checked against the groups already streamed
                                // into this screen — a non-member cannot read the group doc
                                // under the new rules, and does not need to.
                                if (isAlreadyMemberOf(groupId)) {
                                    Toast.makeText(getContext(), "You are already a member of this group!", Toast.LENGTH_SHORT).show();
                                    navigateToGroupDetail(groupId);
                                    return;
                                }

                                // 3. Build joining member map
                                Map<String, Object> memberMap = new HashMap<>();
                                memberMap.put("uid", uid);
                                memberMap.put("name", finalJoinerName);
                                memberMap.put("role", "member");
                                memberMap.put("joinedAt", Timestamp.now());

                                // 4. Perform atomic update. arrayUnion is idempotent, and the
                                //    post-write state puts this uid in memberUids, which is
                                //    what the rule's self-join clause authorizes against.
                                mFirestore.collection("groups").document(groupId)
                                        .update(
                                                "members", FieldValue.arrayUnion(memberMap),
                                                "memberUids", FieldValue.arrayUnion(uid)
                                        )
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(getContext(), "Successfully joined group!", Toast.LENGTH_SHORT).show();
                                            binding.etInviteCode.setText("");
                                            navigateToGroupDetail(groupId);
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(getContext(), "Failed to join: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Search failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
                });
    }

    /** True when the signed-in user already belongs to {@code groupId}. */
    private boolean isAlreadyMemberOf(String groupId) {
        for (DocumentSnapshot doc : groupsList) {
            if (doc.getId().equals(groupId)) return true;
        }
        return false;
    }

    private void navigateToGroupDetail(String groupId) {
        Intent intent = new Intent(getContext(), GroupDetailActivity.class);
        intent.putExtra("groupId", groupId);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (groupsListener != null) {
            groupsListener.remove();
        }
        binding = null;
    }

    // --- Inner Group Adapter ---
    private class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemGroupBinding itemBinding = ItemGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new GroupViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            DocumentSnapshot doc = groupsList.get(position);
            holder.bind(doc);
        }

        @Override
        public int getItemCount() {
            return groupsList.size();
        }

        class GroupViewHolder extends RecyclerView.ViewHolder {
            private final ItemGroupBinding itemBinding;

            public GroupViewHolder(@NonNull ItemGroupBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void bind(DocumentSnapshot doc) {
                String id = doc.getString("id");
                String name = doc.getString("name");
                String type = doc.getString("type");
                Double balance = doc.getDouble("balance");
                List<Map<String, Object>> members = (List<Map<String, Object>>) doc.get("members");

                itemBinding.txtGroupName.setText(name != null ? name : "Group Wallet");
                itemBinding.txtGroupType.setText(type != null ? type : "Shared");

                int memberCount = members != null ? members.size() : 1;
                itemBinding.txtMemberCount.setText(memberCount + (memberCount == 1 ? " member" : " members"));

                double finalBalance = balance != null ? balance : 0.0;
                itemBinding.txtGroupBalance.setText(CurrencyManager.getInstance().formatAmount(finalBalance));

                // Color tint category badge depending on Group Type
                int tintColor = Color.parseColor("#7C6FE0"); // Family (purple)
                int bgColor = Color.parseColor("#1A7C6FE0");
                if ("Friends".equalsIgnoreCase(type)) {
                    tintColor = Color.parseColor("#5DCAA5"); // Green
                    bgColor = Color.parseColor("#1A5DCAA5");
                } else if ("Organisation".equalsIgnoreCase(type)) {
                    tintColor = Color.parseColor("#EF9F27"); // Amber
                    bgColor = Color.parseColor("#1AEF9F27");
                } else if ("Event Fund".equalsIgnoreCase(type)) {
                    tintColor = Color.parseColor("#E24B4A"); // Danger
                    bgColor = Color.parseColor("#1AE24B4A");
                }

                itemBinding.txtGroupType.setTextColor(tintColor);
                itemBinding.txtGroupType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));

                itemView.setOnClickListener(v -> navigateToGroupDetail(id));
            }
        }
    }
}
