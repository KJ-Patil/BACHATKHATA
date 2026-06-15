package com.example.bachatkhata;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.example.bachatkhata.databinding.ActivityReceiptScannerBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptScannerActivity extends BaseActivity {
    private static final String TAG = "ReceiptScanner";
    private ActivityReceiptScannerBinding binding;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private boolean isLaunchedFromAddTxn = false;

    private final ActivityResultLauncher<String> requestCameraLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to scan receipts.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
    );

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processGalleryImage(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceiptScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isLaunchedFromAddTxn = getIntent().getBooleanExtra("launchedFromAddTransaction", false);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();

        setupListeners();
        checkPermissionsAndStart();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCapture.setOnClickListener(v -> captureImage());
        binding.btnGallery.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
                Toast.makeText(this, "Failed to initialize camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (imageCapture == null) return;

        binding.layoutScanningProgress.setVisibility(View.VISIBLE);

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull androidx.camera.core.ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();

                if (bitmap != null) {
                    runOnUiThread(() -> {
                        binding.imgCapturePreview.setImageBitmap(bitmap);
                        binding.imgCapturePreview.setVisibility(View.VISIBLE);
                    });
                    processImageWithMLKit(bitmap);
                } else {
                    runOnUiThread(() -> {
                        binding.layoutScanningProgress.setVisibility(View.GONE);
                        Toast.makeText(ReceiptScannerActivity.this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                runOnUiThread(() -> {
                    binding.layoutScanningProgress.setVisibility(View.GONE);
                    Toast.makeText(ReceiptScannerActivity.this, "Photo capture failed", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private Bitmap imageProxyToBitmap(androidx.camera.core.ImageProxy image) {
        try {
            android.media.Image mediaImage = image.getImage();
            if (mediaImage != null) {
                java.nio.ByteBuffer buffer = mediaImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting image proxy to bitmap", e);
        }
        return null;
    }

    private void processGalleryImage(Uri uri) {
        binding.layoutScanningProgress.setVisibility(View.VISIBLE);
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                binding.imgCapturePreview.setImageBitmap(bitmap);
                binding.imgCapturePreview.setVisibility(View.VISIBLE);
                processImageWithMLKit(bitmap);
            } else {
                binding.layoutScanningProgress.setVisibility(View.GONE);
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Gallery image loading failed", e);
            binding.layoutScanningProgress.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to read gallery image", Toast.LENGTH_SHORT).show();
        }
    }

    private void processImageWithMLKit(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> parseReceiptText(visionText.getText(), visionText))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR recognition failed", e);
                    runOnUiThread(() -> {
                        binding.layoutScanningProgress.setVisibility(View.GONE);
                        Toast.makeText(ReceiptScannerActivity.this, "Failed to recognize text", Toast.LENGTH_SHORT).show();
                    });
                });
    }

    private void parseReceiptText(String text, Text visionText) {
        double amount = 0.0;
        String merchant = "Unknown Merchant";
        Date parsedDate = new Date();

        Log.d(TAG, "Receipt Text:\n" + text);

        // 1. Parse Merchant (Largest bounding box area is likely the merchant header logo/name)
        double maxArea = 0;
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            android.graphics.Rect rect = block.getBoundingBox();
            if (rect != null) {
                double area = rect.width() * rect.height();
                if (area > maxArea && block.getText().trim().length() > 2) {
                    maxArea = area;
                    merchant = block.getText().split("\n")[0].trim();
                }
            }
        }
        if (merchant.equals("Unknown Merchant") && !visionText.getTextBlocks().isEmpty()) {
            merchant = visionText.getTextBlocks().get(0).getText().split("\n")[0].trim();
        }

        // Clean up merchant name (truncate extra lines or symbols)
        if (merchant.length() > 50) {
            merchant = merchant.substring(0, 50).trim();
        }

        // 2. Parse Amount (Find decimal numbers or expressions like Total, Net, Rs, ₹)
        // Extract all candidate values, find the largest numeric value
        Pattern amountPattern = Pattern.compile("(?:rs\\.?|inr|₹)?\\s*([\\d,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(text);
        
        double maxVal = 0.0;
        while (amountMatcher.find()) {
            try {
                String valStr = amountMatcher.group(1).replace(",", "");
                double val = Double.parseDouble(valStr);
                // Exclude common phone/date numbers by checking typical value range
                if (val > maxVal && val < 500000.0) {
                    maxVal = val;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (maxVal > 0) {
            amount = maxVal;
        } else {
            // Check for integer numbers if no decimals match
            Pattern intPattern = Pattern.compile("(?:rs\\.?|inr|₹)\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
            Matcher intMatcher = intPattern.matcher(text);
            while (intMatcher.find()) {
                try {
                    String valStr = intMatcher.group(1).replace(",", "");
                    double val = Double.parseDouble(valStr);
                    if (val > maxVal && val < 100000.0) {
                        maxVal = val;
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            amount = maxVal;
        }

        // 3. Parse Date (DD/MM/YYYY, DD-MM-YYYY, DD MMM YYYY)
        Pattern datePattern = Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2,4})");
        Matcher dateMatcher = datePattern.matcher(text);
        if (dateMatcher.find()) {
            try {
                int day = Integer.parseInt(dateMatcher.group(1));
                int month = Integer.parseInt(dateMatcher.group(2)) - 1; // Calendar month is 0-indexed
                int year = Integer.parseInt(dateMatcher.group(3));
                if (year < 100) year += 2000; // Handle 2-digit years
                
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(year, month, day);
                parsedDate = cal.getTime();
            } catch (Exception e) {
                // Ignore
            }
        } else {
            // Try word months: DD MMM YYYY (e.g. 15 Jun 2026)
            Pattern wordDatePattern = Pattern.compile("(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
            Matcher wordMatcher = wordDatePattern.matcher(text);
            if (wordMatcher.find()) {
                try {
                    int day = Integer.parseInt(wordMatcher.group(1));
                    String monthStr = wordMatcher.group(2).toLowerCase();
                    int year = Integer.parseInt(wordMatcher.group(3));
                    int month = 0;
                    if (monthStr.contains("jan")) month = 0;
                    else if (monthStr.contains("feb")) month = 1;
                    else if (monthStr.contains("mar")) month = 2;
                    else if (monthStr.contains("apr")) month = 3;
                    else if (monthStr.contains("may")) month = 4;
                    else if (monthStr.contains("jun")) month = 5;
                    else if (monthStr.contains("jul")) month = 6;
                    else if (monthStr.contains("aug")) month = 7;
                    else if (monthStr.contains("sep")) month = 8;
                    else if (monthStr.contains("oct")) month = 9;
                    else if (monthStr.contains("nov")) month = 10;
                    else if (monthStr.contains("dec")) month = 11;
                    
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.set(year, month, day);
                    parsedDate = cal.getTime();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        final double finalAmount = amount;
        final String finalMerchant = merchant;
        final Date finalDate = parsedDate;

        runOnUiThread(() -> {
            binding.layoutScanningProgress.setVisibility(View.GONE);
            
            if (isLaunchedFromAddTxn) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("amount", finalAmount);
                resultIntent.putExtra("merchant", finalMerchant);
                resultIntent.putExtra("date", finalDate.getTime());
                setResult(RESULT_OK, resultIntent);
                finish();
            } else {
                // Launch SmsImportBottomSheet directly
                SmsParser.ParsedTransaction txn = new SmsParser.ParsedTransaction();
                txn.amount = finalAmount;
                txn.merchant = finalMerchant;
                txn.date = finalDate;
                txn.type = "expense";
                
                SmsImportBottomSheet sheet = SmsImportBottomSheet.newInstance(txn);
                sheet.show(getSupportFragmentManager(), "SmsImportBottomSheet");
                
                // Reset UI after sheet is dismissed
                sheet.setOnDismissListener(() -> {
                    binding.imgCapturePreview.setVisibility(View.GONE);
                    startCamera();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}
