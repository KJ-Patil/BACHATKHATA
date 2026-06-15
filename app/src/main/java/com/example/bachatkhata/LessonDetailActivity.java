package com.example.bachatkhata;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.example.bachatkhata.databinding.ActivityLessonDetailBinding;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LessonDetailActivity extends BaseActivity {

    private ActivityLessonDetailBinding binding;
    private String lessonId;
    private String lessonTitle;
    private String uid;

    private final List<QuizQuestion> quizQuestions = new ArrayList<>();
    private final List<RadioGroup> radioGroupsList = new ArrayList<>();

    private static class QuizQuestion {
        String question;
        List<String> options = new ArrayList<>();
        int correctIndex;

        QuizQuestion(String question, List<String> options, int correctIndex) {
            this.question = question;
            this.options.addAll(options);
            this.correctIndex = correctIndex;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLessonDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        lessonId = getIntent().getStringExtra("lessonId");
        lessonTitle = getIntent().getStringExtra("lessonTitle");

        if (lessonId == null) {
            Toast.makeText(this, "Lesson details missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.txtTitle.setText("Financial School");

        loadLessonContent();
        binding.btnSubmitQuiz.setOnClickListener(v -> checkQuizAnswers());
    }

    private void loadLessonContent() {
        String jsonContent = null;
        try {
            InputStream is = getAssets().open("lessons/" + lessonId + ".json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            jsonContent = new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Asset load failed, fallback to smart generation
            jsonContent = getFallbackLessonJson(lessonId);
        }

        if (jsonContent == null) {
            Toast.makeText(this, "Could not load lesson content", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            JSONObject mainObj = new JSONObject(jsonContent);
            String title = mainObj.getString("title");
            binding.txtLessonDetailTitle.setText(title);

            // Populate sections
            JSONArray sectionsArray = mainObj.getJSONArray("sections");
            binding.layoutSections.removeAllViews();

            for (int i = 0; i < sectionsArray.length(); i++) {
                JSONObject sectionObj = sectionsArray.getJSONObject(i);
                String heading = sectionObj.getString("heading");
                String body = sectionObj.getString("body");

                addSectionCard(heading, body);
            }

            // Populate quiz
            JSONArray quizArray = mainObj.getJSONArray("quiz");
            binding.layoutQuiz.removeAllViews();
            quizQuestions.clear();
            radioGroupsList.clear();

            for (int i = 0; i < quizArray.length(); i++) {
                JSONObject quizObj = quizArray.getJSONObject(i);
                String question = quizObj.getString("question");
                JSONArray optionsArray = quizObj.getJSONArray("options");
                int correctIndex = quizObj.getInt("correctIndex");

                List<String> options = new ArrayList<>();
                for (int j = 0; j < optionsArray.length(); j++) {
                    options.add(optionsArray.getString(j));
                }

                QuizQuestion q = new QuizQuestion(question, options, correctIndex);
                quizQuestions.add(q);
                addQuizQuestionView(i + 1, q);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing lesson file", Toast.LENGTH_SHORT).show();
        }
    }

    private void addSectionCard(String heading, String body) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);
        card.setCardElevation(2);
        card.setRadius(16);
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(Color.parseColor("#E0DEFF"));
        card.setStrokeWidth(1);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        TextView txtHeading = new TextView(this);
        txtHeading.setText(heading);
        txtHeading.setTextSize(16f);
        txtHeading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtHeading.setTextColor(Color.parseColor("#7C6FE0"));
        txtHeading.setPadding(0, 0, 0, 8);
        layout.addView(txtHeading);

        TextView txtBody = new TextView(this);
        txtBody.setText(body);
        txtBody.setTextSize(14f);
        txtBody.setLineSpacing(4f, 1.1f);
        txtBody.setTextColor(Color.parseColor("#1A1A2E"));
        layout.addView(txtBody);

        card.addView(layout);
        binding.layoutSections.addView(card);
    }

    private void addQuizQuestionView(int number, QuizQuestion q) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 8, 0, 20);

        TextView txtQuestion = new TextView(this);
        txtQuestion.setText(String.format(Locale.US, "%d. %s", number, q.question));
        txtQuestion.setTextSize(14f);
        txtQuestion.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtQuestion.setTextColor(Color.parseColor("#1A1A2E"));
        txtQuestion.setPadding(0, 0, 0, 12);
        layout.addView(txtQuestion);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);

        for (int i = 0; i < q.options.size(); i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(q.options.get(i));
            rb.setTextSize(13f);
            rb.setTextColor(Color.parseColor("#1A1A2E"));
            rb.setPadding(8, 8, 8, 8);
            group.addView(rb);
        }

        layout.addView(group);
        radioGroupsList.add(group);
        binding.layoutQuiz.addView(layout);
    }

    private void checkQuizAnswers() {
        int answeredCount = 0;
        for (RadioGroup rg : radioGroupsList) {
            if (rg.getCheckedRadioButtonId() != -1) {
                answeredCount++;
            }
        }

        if (answeredCount < quizQuestions.size()) {
            showSnackbar("Please answer all questions before submitting!", "ERROR");
            return;
        }

        int score = 0;
        for (int i = 0; i < quizQuestions.size(); i++) {
            QuizQuestion q = quizQuestions.get(i);
            RadioGroup rg = radioGroupsList.get(i);
            int checkedId = rg.getCheckedRadioButtonId();
            View radioButton = rg.findViewById(checkedId);
            int selectedIndex = rg.indexOfChild(radioButton);

            if (selectedIndex == q.correctIndex) {
                score++;
            }
        }

        boolean passed = score >= 2; // Pass criteria is at least 2 correct out of 3

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(passed ? "Quiz Passed! 🎉" : "Quiz Failed ❌");
        builder.setMessage(String.format(Locale.US, "You scored %d out of %d.", score, quizQuestions.size()));

        if (passed) {
            builder.setPositiveButton("Awesome", (dialog, which) -> {
                awardBadgeAndFinish();
            });
        } else {
            builder.setPositiveButton("Try Again", null);
        }

        builder.show();
    }

    private void awardBadgeAndFinish() {
        showLoadingDialog();
        GamificationManager.getInstance().checkAndAwardBadge(uid, "lesson_" + lessonId);
        
        // Wait briefly for badge update to register
        binding.btnSubmitQuiz.postDelayed(() -> {
            hideLoadingDialog();
            setResult(RESULT_OK);
            finish();
        }, 800);
    }

    private String getFallbackLessonJson(String id) {
        // Safe mock contents for lessons 4 to 10
        String title = "Personal Finance Concept";
        String section1Head = "What is this topic about?";
        String section1Body = "This module covers essential fundamentals of financial literacy to help you manage your money wisely.";
        String section2Head = "Best Practices";
        String section2Body = "Review statements regularly, avoid unnecessary debt, keep tracking expenses daily, and save for retirement early.";
        
        String q1 = "What is the key rule of personal budgeting?";
        String opt1_1 = "Spend everything you earn";
        String opt1_2 = "Save first, spend what is left";
        String opt1_3 = "Avoid saving altogether";
        int c1 = 1;

        if (id.contains("03")) {
            title = "Compound Interest Magic";
            section1Body = "Compound interest is earning interest on interest, leading to exponential asset growth.";
        } else if (id.contains("04")) {
            title = "UPI and Digital Payments";
            section1Body = "Unified Payments Interface (UPI) is a real-time payment system developed by National Payments Corporation of India (NPCI) enabling instant inter-bank transfers.";
            q1 = "Who developed UPI in India?";
            opt1_1 = "Reserve Bank of India";
            opt1_2 = "National Payments Corporation of India (NPCI)";
            opt1_3 = "State Bank of India";
            c1 = 1;
        } else if (id.contains("05")) {
            title = "What is a Mutual Fund?";
            section1Body = "A mutual fund pools money from multiple investors to purchase securities like stocks, bonds, and short-term debt.";
            q1 = "What is a Mutual Fund?";
            opt1_1 = "An individual stock";
            opt1_2 = "A pooled investment vehicle";
            opt1_3 = "A bank deposit";
            c1 = 1;
        } else if (id.contains("06")) {
            title = "SIP vs Lump Sum Investing";
            section1Body = "Systematic Investment Plan (SIP) allows investing small fixed amounts regularly, reducing timing risks, whereas lump sum is a one-time big investment.";
        } else if (id.contains("07")) {
            title = "Read your Bank Statement";
            section1Body = "Bank statements record debits, credits, account fees, and interest earned. Always reconcile them monthly.";
        } else if (id.contains("08")) {
            title = "Income Tax Basics (India)";
            section1Body = "Income tax is levied by the government on individual income. India has a progressive tax slab system.";
        } else if (id.contains("09")) {
            title = "What is Inflation?";
            section1Body = "Inflation is the general increase in prices and fall in the purchasing value of money over time.";
        } else if (id.contains("10")) {
            title = "Building an Emergency Fund";
            section1Body = "An emergency fund should cover 3 to 6 months of living expenses, kept in liquid accounts for urgent needs.";
        }

        return String.format(Locale.US,
            "{\"id\":\"%s\",\"title\":\"%s\",\"sections\":[{\"heading\":\"%s\",\"body\":\"%s\"},{\"heading\":\"%s\",\"body\":\"%s\"}],\"quiz\":[{\"question\":\"%s\",\"options\":[\"%s\",\"%s\",\"%s\"],\"correctIndex\":%d},{\"question\":\"Is consistency important in managing wealth?\",\"options\":[\"No, timing is everything\",\"Yes, steady habits beat short wins\",\"Only if you have high income\"],\"correctIndex\":1},{\"question\":\"When is the best time to start saving?\",\"options\":[\"Tomorrow\",\"When salary increases\",\"As early as possible\"],\"correctIndex\":2}]}",
            id, title, section1Head, section1Body, section2Head, section2Body, q1, opt1_1, opt1_2, opt1_3, c1
        );
    }
}
