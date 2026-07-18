package com.example.bachatkhata;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bachatkhata.databinding.FragmentLiteracyLessonsBinding;
import com.example.bachatkhata.databinding.ItemLessonBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LiteracyLessonsFragment extends Fragment {

    private FragmentLiteracyLessonsBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;
    private String uid;

    private final List<LessonModel> lessonsList = new ArrayList<>();
    private final List<String> completedBadges = new ArrayList<>();
    private LessonAdapter adapter;

    private ActivityResultLauncher<Intent> lessonLauncher;

    public enum LessonStatus {
        COMPLETED,
        CURRENT,
        LOCKED
    }

    public static class LessonModel {
        public String id;
        public String title;
        public String topic;
        public int readTimeMinutes;
        public String prerequisiteId;
        public String icon;
        public LessonStatus status = LessonStatus.LOCKED;

        public LessonModel(String id, String title, String topic, int readTimeMinutes, String prerequisiteId, String icon) {
            this.id = id;
            this.title = title;
            this.topic = topic;
            this.readTimeMinutes = readTimeMinutes;
            this.prerequisiteId = prerequisiteId;
            this.icon = icon;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            uid = mAuth.getCurrentUser().getUid();
        }

        lessonLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                        loadUserBadges(); // refresh states
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLiteracyLessonsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupUI();
        loadLessonsMetadata();
        loadUserBadges();
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.rvLessons.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LessonAdapter();
        binding.rvLessons.setAdapter(adapter);
    }

    private void loadLessonsMetadata() {
        try {
            InputStream is = requireContext().getAssets().open("lessons.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(json);

            lessonsList.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.getString("id");
                String title = obj.getString("title");
                String topic = obj.getString("topic");
                int time = obj.getInt("readTimeMinutes");
                String prereq = obj.optString("prerequisiteId", "");
                String icon = obj.optString("icon", "");

                lessonsList.add(new LessonModel(id, title, topic, time, prereq, icon));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadUserBadges() {
        if (uid == null) return;

        mFirestore.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    completedBadges.clear();
                    if (doc.exists()) {
                        List<String> badges = (List<String>) doc.get("awardedBadges");
                        if (badges != null) {
                            completedBadges.addAll(badges);
                        }
                    }

                    updateLessonStates();
                });
    }

    private void updateLessonStates() {
        int completedCount = 0;

        for (LessonModel l : lessonsList) {
            String badgeName = "lesson_" + l.id;
            
            if (completedBadges.contains(badgeName)) {
                l.status = LessonStatus.COMPLETED;
                completedCount++;
            } else if (l.prerequisiteId.isEmpty() || completedBadges.contains("lesson_" + l.prerequisiteId)) {
                l.status = LessonStatus.CURRENT;
            } else {
                l.status = LessonStatus.LOCKED;
            }
        }

        // Update progress card
        int total = lessonsList.size();
        binding.txtProgressSummary.setText(String.format(java.util.Locale.US, "%d of %d lessons complete", completedCount, total));
        if (total > 0) {
            int pct = (completedCount * 100) / total;
            binding.progressBar.setProgress(pct);
        }

        adapter.notifyDataSetChanged();
    }

    private void startLesson(LessonModel lesson) {
        if (lesson.status == LessonStatus.LOCKED) return;

        Intent intent = new Intent(getContext(), LessonDetailActivity.class);
        intent.putExtra("lessonId", lesson.id);
        intent.putExtra("lessonTitle", lesson.title);
        lessonLauncher.launch(intent);
    }

    // Recycler Adapter
    private class LessonAdapter extends RecyclerView.Adapter<LessonAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemLessonBinding binding = ItemLessonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            LessonModel l = lessonsList.get(position);
            holder.bind(l);
        }

        @Override
        public int getItemCount() {
            return lessonsList.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ItemLessonBinding lBinding;

            Holder(ItemLessonBinding binding) {
                super(binding.getRoot());
                lBinding = binding;
            }

            void bind(LessonModel l) {
                lBinding.txtLessonTitle.setText(l.title);
                lBinding.txtTopicBadge.setText(l.topic);
                lBinding.txtReadTime.setText(l.readTimeMinutes + " min read");

                // Icon setup based on topic / status
                int tintColor;
                if (l.status == LessonStatus.COMPLETED) {
                    lBinding.getRoot().setAlpha(1.0f);
                    lBinding.imgStatus.setImageResource(R.drawable.ic_check); // Use checked state indicator
                    lBinding.imgStatus.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colorSecondary)));
                    lBinding.btnStart.setVisibility(View.GONE);
                    lBinding.getRoot().setClickable(true);
                    lBinding.getRoot().setOnClickListener(v -> startLesson(l));
                } else if (l.status == LessonStatus.CURRENT) {
                    lBinding.getRoot().setAlpha(1.0f);
                    lBinding.imgStatus.setImageResource(R.drawable.ic_wallet); // play/start icon indicator
                    lBinding.imgStatus.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
                    lBinding.btnStart.setVisibility(View.VISIBLE);
                    lBinding.btnStart.setOnClickListener(v -> startLesson(l));
                    lBinding.getRoot().setClickable(true);
                    lBinding.getRoot().setOnClickListener(v -> startLesson(l));
                } else {
                    // Locked
                    lBinding.getRoot().setAlpha(0.6f);
                    lBinding.imgStatus.setImageResource(R.drawable.ic_lock); // locked pad lock icon indicator
                    lBinding.imgStatus.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
                    lBinding.btnStart.setVisibility(View.GONE);
                    lBinding.getRoot().setClickable(false);
                    lBinding.getRoot().setOnClickListener(null);
                }
            }
        }
    }
}
