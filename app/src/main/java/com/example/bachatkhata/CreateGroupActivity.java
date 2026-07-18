package com.example.bachatkhata;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.bachatkhata.databinding.ActivityCreateGroupBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class CreateGroupActivity extends BaseActivity {

    private ActivityCreateGroupBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String selectedType = "Family"; // Default type
    private final List<Map<String, String>> selectedMembers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupUI();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());

        // Setup Selection states for type cards
        updateTypeSelectionUI();

        binding.cardTypeFamily.setOnClickListener(v -> {
            selectedType = "Family";
            updateTypeSelectionUI();
        });
        binding.cardTypeFriends.setOnClickListener(v -> {
            selectedType = "Friends";
            updateTypeSelectionUI();
        });
        binding.cardTypeOrg.setOnClickListener(v -> {
            selectedType = "Organisation";
            updateTypeSelectionUI();
        });
        binding.cardTypeEvent.setOnClickListener(v -> {
            selectedType = "Event Fund";
            updateTypeSelectionUI();
        });

        // Contact Picker Trigger — opens the in-app contact book (multi-select).
        binding.btnPickContact.setOnClickListener(v -> {
            ContactPickerBottomSheet picker = ContactPickerBottomSheet.newInstance(true);
            picker.setListener(contacts -> {
                for (ContactPickerBottomSheet.Contact c : contacts) {
                    addMemberToList(c.name != null ? c.name : c.phone, c.phone);
                }
            });
            picker.show(getSupportFragmentManager(), "contact_picker");
        });

        // Manual Add Member button
        binding.btnAddMember.setOnClickListener(v -> {
            String phone = binding.etMemberPhone.getText().toString().trim();
            if (phone.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            addMemberToList(phone, phone);
            binding.etMemberPhone.setText("");
        });

        // Create Group
        binding.btnCreateGroup.setOnClickListener(v -> createGroupInFirestore());
    }

    private void updateTypeSelectionUI() {
        // Reset card backgrounds
        binding.cardTypeFamily.setBackgroundColor(Color.WHITE);
        binding.cardTypeFamily.setStrokeColor(Color.parseColor("#E0DEFF"));
        binding.cardTypeFriends.setBackgroundColor(Color.WHITE);
        binding.cardTypeFriends.setStrokeColor(Color.parseColor("#E0DEFF"));
        binding.cardTypeOrg.setBackgroundColor(Color.WHITE);
        binding.cardTypeOrg.setStrokeColor(Color.parseColor("#E0DEFF"));
        binding.cardTypeEvent.setBackgroundColor(Color.WHITE);
        binding.cardTypeEvent.setStrokeColor(Color.parseColor("#E0DEFF"));

        // Highlight selected
        switch (selectedType) {
            case "Family":
                binding.cardTypeFamily.setBackgroundColor(Color.parseColor("#1A7C6FE0")); // 10% primary
                binding.cardTypeFamily.setStrokeColor(Color.parseColor("#7C6FE0"));
                break;
            case "Friends":
                binding.cardTypeFriends.setBackgroundColor(Color.parseColor("#1A5DCAA5")); // 10% secondary
                binding.cardTypeFriends.setStrokeColor(Color.parseColor("#5DCAA5"));
                break;
            case "Organisation":
                binding.cardTypeOrg.setBackgroundColor(Color.parseColor("#1AEF9F27")); // 10% accent
                binding.cardTypeOrg.setStrokeColor(Color.parseColor("#EF9F27"));
                break;
            case "Event Fund":
                binding.cardTypeEvent.setBackgroundColor(Color.parseColor("#1AE24B4A")); // 10% danger
                binding.cardTypeEvent.setStrokeColor(Color.parseColor("#E24B4A"));
                break;
        }
    }

    private void addMemberToList(String name, String phone) {
        // Check if phone already added
        for (Map<String, String> m : selectedMembers) {
            if (phone.equals(m.get("phone"))) {
                Toast.makeText(this, "Member already added", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Map<String, String> member = new HashMap<>();
        member.put("name", name);
        member.put("phone", phone);
        selectedMembers.add(member);

        refreshSelectedMembersUI();
    }

    private void refreshSelectedMembersUI() {
        binding.layoutSelectedMembers.removeAllViews();
        if (selectedMembers.isEmpty()) {
            binding.cardMembersContainer.setVisibility(View.GONE);
            return;
        }

        binding.cardMembersContainer.setVisibility(View.VISIBLE);

        for (int i = 0; i < selectedMembers.size(); i++) {
            Map<String, String> m = selectedMembers.get(i);
            View view = LayoutInflater.from(this).inflate(R.layout.item_category_manage, binding.layoutSelectedMembers, false);

            TextView txtName = view.findViewById(R.id.txtCategoryName);
            TextView txtSubText = view.findViewById(R.id.txtCategoryType); // Reuse subtext
            ImageView btnDelete = view.findViewById(R.id.btnDeleteCategory);

            txtName.setText(m.get("name"));
            txtSubText.setText(m.get("phone"));
            txtSubText.setVisibility(View.VISIBLE);

            final int index = i;
            btnDelete.setOnClickListener(v -> {
                selectedMembers.remove(index);
                refreshSelectedMembersUI();
            });

            binding.layoutSelectedMembers.addView(view);
        }
    }

    private void createGroupInFirestore() {
        String groupName = binding.etGroupName.getText().toString().trim();
        if (groupName.isEmpty()) {
            Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // 1. Generate 6-digit numeric invite code
        String inviteCode = String.format(Locale.US, "%06d", new Random().nextInt(1000000));

        // 2. Fetch current user's profile details to add as admin
        mFirestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String adminName = "Admin";
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.trim().isEmpty()) {
                            adminName = name;
                        }
                    }

                    saveGroupDocument(uid, adminName, groupName, inviteCode);
                })
                .addOnFailureListener(e -> {
                    saveGroupDocument(uid, "Admin", groupName, inviteCode);
                });
    }

    private void saveGroupDocument(String uid, String adminName, String groupName, String inviteCode) {
        String groupId = mFirestore.collection("groups").document().getId();

        // Admin member details
        Map<String, Object> adminMember = new HashMap<>();
        adminMember.put("uid", uid);
        adminMember.put("name", adminName);
        adminMember.put("role", "admin");
        adminMember.put("joinedAt", Timestamp.now());

        List<Map<String, Object>> membersList = new ArrayList<>();
        membersList.add(adminMember);

        // Map selected offline contacts as pending members
        for (Map<String, String> m : selectedMembers) {
            Map<String, Object> member = new HashMap<>();
            member.put("uid", ""); // Blank uid signifies pending signup / code joining
            member.put("name", m.get("name"));
            member.put("phone", m.get("phone"));
            member.put("role", "member");
            member.put("joinedAt", null);
            membersList.add(member);
        }

        List<String> memberUids = new ArrayList<>();
        memberUids.add(uid); // Admin uid starts

        Map<String, Object> groupMap = new HashMap<>();
        groupMap.put("id", groupId);
        groupMap.put("name", groupName);
        groupMap.put("type", selectedType);
        groupMap.put("createdBy", uid);
        groupMap.put("inviteCode", inviteCode);
        groupMap.put("members", membersList);
        groupMap.put("memberUids", memberUids);
        groupMap.put("balance", 0.0);
        groupMap.put("createdAt", Timestamp.now());

        mFirestore.collection("groups").document(groupId)
                .set(groupMap)
                .addOnSuccessListener(aVoid -> {
                    showSnackbar("Group Wallet Created!", "SUCCESS");
                    Intent intent = new Intent(this, GroupDetailActivity.class);
                    intent.putExtra("groupId", groupId);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create group: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
