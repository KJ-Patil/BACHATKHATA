package com.example.bachatkhata;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bachatkhata.databinding.SheetCalculatorBinding;
import com.example.bachatkhata.domain.MathEvaluator;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Keypad for the floating calculator bubble. Shows a live result as the user
 * types — {@link MathEvaluator#tryEvaluate} returns null for a half-typed
 * expression, so an incomplete "12 +" simply shows nothing rather than an error.
 */
public class CalculatorBottomSheet extends BottomSheetDialogFragment {

    private SheetCalculatorBinding binding;

    /** The expression as typed, using ASCII operators the evaluator understands. */
    private final StringBuilder expression = new StringBuilder();

    /** Last successfully evaluated value, for copy-to-clipboard and '='. */
    private Double lastResult = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetCalculatorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wireKeypad();
        render();
    }

    private void wireKeypad() {
        binding.btn0.setOnClickListener(v -> append("0"));
        binding.btn1.setOnClickListener(v -> append("1"));
        binding.btn2.setOnClickListener(v -> append("2"));
        binding.btn3.setOnClickListener(v -> append("3"));
        binding.btn4.setOnClickListener(v -> append("4"));
        binding.btn5.setOnClickListener(v -> append("5"));
        binding.btn6.setOnClickListener(v -> append("6"));
        binding.btn7.setOnClickListener(v -> append("7"));
        binding.btn8.setOnClickListener(v -> append("8"));
        binding.btn9.setOnClickListener(v -> append("9"));
        binding.btnDot.setOnClickListener(v -> append("."));

        // The keys show ÷ × − but the evaluator only accepts ASCII.
        binding.btnPlus.setOnClickListener(v -> append("+"));
        binding.btnMinus.setOnClickListener(v -> append("-"));
        binding.btnMultiply.setOnClickListener(v -> append("*"));
        binding.btnDivide.setOnClickListener(v -> append("/"));
        binding.btnParenOpen.setOnClickListener(v -> append("("));
        binding.btnParenClose.setOnClickListener(v -> append(")"));

        binding.btnClear.setOnClickListener(v -> {
            expression.setLength(0);
            lastResult = null;
            render();
        });

        binding.btnBackspace.setOnClickListener(v -> {
            if (expression.length() > 0) {
                expression.deleteCharAt(expression.length() - 1);
            }
            render();
        });

        // '=' collapses the expression to its value so the user can keep working
        // from the result, the way a physical calculator behaves.
        binding.btnEquals.setOnClickListener(v -> {
            try {
                double value = MathEvaluator.evaluate(expression.toString());
                lastResult = value;
                expression.setLength(0);
                expression.append(format(value));
                render();
            } catch (RuntimeException e) {
                binding.txtResult.setText(e.getMessage());
            }
        });

        binding.btnCopyResult.setOnClickListener(v -> copyResult());
    }

    private void append(String token) {
        expression.append(token);
        render();
    }

    private void render() {
        binding.txtExpression.setText(expression.length() == 0 ? "0" : display(expression.toString()));

        Double value = MathEvaluator.tryEvaluate(expression.toString());
        lastResult = value;
        binding.txtResult.setText(value == null ? "" : "= " + format(value));
    }

    /** Swaps ASCII operators back to their typographic forms for display only. */
    private String display(String raw) {
        return raw.replace("*", " × ").replace("/", " ÷ ")
                .replace("+", " + ").replace("-", " − ");
    }

    /**
     * Trims binary-float noise (0.1+0.2 would otherwise read 0.30000000000000004)
     * and drops a trailing ".0" so whole numbers look like whole numbers.
     */
    private String format(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return rounded.toPlainString();
    }

    private void copyResult() {
        if (lastResult == null) {
            Toast.makeText(getContext(), R.string.calc_nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        Context context = getContext();
        if (context == null) return;

        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;

        clipboard.setPrimaryClip(ClipData.newPlainText("Calculator result", format(lastResult)));
        Toast.makeText(context, R.string.calc_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
