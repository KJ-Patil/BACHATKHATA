package com.example.bachatkhata;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceLoggingHelper {
    private static final String TAG = "VoiceLoggingHelper";
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    public interface VoiceLoggingCallback {
        void onListeningStarted();
        void onListeningStopped();
        void onVoiceParsed(double amount, String categoryName, String type, String rawText);
        void onError(String errorMsg);
    }

    public void startListening(Context context, VoiceLoggingCallback callback) {
        if (isListening) {
            stopListening();
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("Speech recognition not available on this device");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"); // Hindi & Indian English support
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                callback.onListeningStarted();
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                isListening = false;
                callback.onListeningStopped();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                callback.onListeningStopped();
                String message;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
                    case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Permission required"; break;
                    case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout"; break;
                    case SpeechRecognizer.ERROR_NO_MATCH: message = "No match found. Try again."; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "Speech service busy"; break;
                    case SpeechRecognizer.ERROR_SERVER: message = "Server error"; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input"; break;
                    default: message = "Unknown error"; break;
                }
                callback.onError(message);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.d(TAG, "Recognized: " + recognizedText);
                    parseSpeechText(recognizedText, callback);
                } else {
                    callback.onError("No text recognized");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
    }

    private void parseSpeechText(String text, VoiceLoggingCallback callback) {
        String lower = text.toLowerCase().trim();

        // 1. Determine Type (Income vs Expense)
        String type = "expense"; // Default
        if (lower.contains("salary") || lower.contains("vetan") || lower.contains("income") ||
            lower.contains("mila") || lower.contains("mili") || lower.contains("received") || 
            lower.contains("aaya") || lower.contains("aayi") || lower.contains("jama") || 
            lower.contains("credit") || lower.contains("deposit") || lower.contains("freelance")) {
            type = "income";
        }

        // 2. Parse Amount
        double amount = parseAmount(lower);

        // 3. Parse Category
        String category = "Other"; // Default fallback
        if (type.equals("income")) {
            category = "Other income";
            if (lower.contains("salary") || lower.contains("vetan")) {
                category = "Salary";
            } else if (lower.contains("freelance") || lower.contains("project") || lower.contains("website") || lower.contains("coding")) {
                category = "Freelance";
            }
        } else {
            if (lower.contains("khana") || lower.contains("food") || lower.contains("hotel") || 
                lower.contains("restaurant") || lower.contains("dinner") || lower.contains("lunch") || 
                lower.contains("swiggy") || lower.contains("zomato") || lower.contains("chai") || 
                lower.contains("samosa") || lower.contains("nasta")) {
                category = "Food";
            } else if (lower.contains("transport") || lower.contains("gadi") || lower.contains("auto") || 
                       lower.contains("cab") || lower.contains("petrol") || lower.contains("diesel") || 
                       lower.contains("bus") || lower.contains("train") || lower.contains("metro") || 
                       lower.contains("ticket") || lower.contains("fare")) {
                category = "Transport";
            } else if (lower.contains("shopping") || lower.contains("kharidari") || lower.contains("kapde") || 
                       lower.contains("shoes") || lower.contains("shirt") || lower.contains("jeans") || 
                       lower.contains("clothes") || lower.contains("mall")) {
                category = "Shopping";
            } else if (lower.contains("bill") || lower.contains("bijli") || lower.contains("light") || 
                       lower.contains("mobile") || lower.contains("recharge") || lower.contains("wifi") || 
                       lower.contains("electricity") || lower.contains("tv") || lower.contains("dth")) {
                category = "Bills";
            } else if (lower.contains("health") || lower.contains("doctor") || lower.contains("medicine") || 
                       lower.contains("dawai") || lower.contains("hospital") || lower.contains("clinic") || 
                       lower.contains("checkup")) {
                category = "Health";
            } else if (lower.contains("movie") || lower.contains("film") || lower.contains("game") || 
                       lower.contains("netflix") || lower.contains("match") || lower.contains("show") || 
                       lower.contains("entertainment") || lower.contains("fun") || lower.contains("party")) {
                category = "Entertainment";
            } else if (lower.contains("education") || lower.contains("school") || lower.contains("college") || 
                       lower.contains("fees") || lower.contains("book") || lower.contains("class") || 
                       lower.contains("tuition") || lower.contains("kitab")) {
                category = "Education";
            } else if (lower.contains("rent") || lower.contains("kiraya") || lower.contains("makan") || 
                       lower.contains("room")) {
                category = "Rent";
            } else if (lower.contains("personal care") || lower.contains("salon") || lower.contains("spa") || 
                       lower.contains("parlour") || lower.contains("barber") || lower.contains("makeup")) {
                category = "Personal care";
            } else if (lower.contains("travel") || lower.contains("flight") || lower.contains("hotel stay") || 
                       lower.contains("vacation") || lower.contains("tour")) {
                category = "Travel";
            } else if (lower.contains("gift") || lower.contains("upahar") || lower.contains("shadi")) {
                category = "Gifts";
            }
        }

        callback.onVoiceParsed(amount, category, type, text);
    }

    private double parseAmount(String text) {
        // 1. Look for explicit numeric sequences (e.g. 500, 1000, 250)
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(text);
        
        long baseNumber = 0;
        boolean foundNumeric = false;
        
        if (matcher.find()) {
            try {
                baseNumber = Long.parseLong(matcher.group(1));
                foundNumeric = true;
            } catch (Exception e) {
                baseNumber = 0;
            }
        }

        // Check for multipliers directly following/before
        if (foundNumeric) {
            double multiplier = 1;
            if (text.contains("hazaar") || text.contains("hazar") || text.contains("thousand") || text.contains("k")) {
                multiplier = 1000;
            } else if (text.contains("lakh") || text.contains("lac") || text.contains("lacs")) {
                multiplier = 100000;
            } else if (text.contains("sau") || text.contains("hundred")) {
                // E.g., "5 hundred"
                multiplier = 100;
            }
            return baseNumber * multiplier;
        }

        // 2. Parse word-based numbers (Hindi/English)
        double total = 0;
        double currentVal = 0;

        // Check for major values
        if (text.contains("sau") || text.contains("hundred")) {
            currentVal = parseWordVal(text, "sau", "hundred");
            total += currentVal * 100;
        }
        
        if (text.contains("hazaar") || text.contains("hazar") || text.contains("thousand")) {
            currentVal = parseWordVal(text, "hazaar", "thousand");
            if (currentVal == 0) currentVal = parseWordVal(text, "hazar", "thousand");
            total += currentVal * 1000;
        }

        // Let's implement a simpler but highly reliable fallback dictionary for direct phrases
        if (total == 0) {
            // E.g. "sau rupaye" -> 100
            if (text.contains("sau") || text.contains("one hundred")) return 100;
            if (text.contains("do sau") || text.contains("two hundred")) return 200;
            if (text.contains("teen sau") || text.contains("three hundred")) return 300;
            if (text.contains("paanch sau") || text.contains("five hundred")) return 500;
            if (text.contains("hazaar") || text.contains("one thousand") || text.contains("ek hazar")) return 1000;
            if (text.contains("do hazar") || text.contains("two thousand") || text.contains("do hazaar")) return 2000;
            if (text.contains("paanch hazar") || text.contains("five thousand") || text.contains("paanch hazaar")) return 5000;
            if (text.contains("das hazar") || text.contains("ten thousand") || text.contains("das hazaar")) return 10000;
        }

        return total;
    }

    private double parseWordVal(String text, String hindiWord, String englishWord) {
        // Find what number word precedes the multiplier word
        String[] words = text.split("\\s+");
        int index = -1;
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals(hindiWord) || words[i].equals(englishWord)) {
                index = i;
                break;
            }
        }

        if (index > 0) {
            String prevWord = words[index - 1];
            return wordToNumber(prevWord);
        }
        return 1; // Default multiplier helper (e.g. "sau rupaye" -> 1 * 100)
    }

    private double wordToNumber(String word) {
        switch (word) {
            case "ek": case "one": return 1;
            case "do": case "two": return 2;
            case "teen": case "three": return 3;
            case "chaar": case "four": return 4;
            case "paanch": case "five": return 5;
            case "chhah": case "six": return 6;
            case "saat": case "seven": return 7;
            case "aath": case "eight": return 8;
            case "nau": case "nine": return 9;
            case "das": case "ten": return 10;
            case "bees": case "twenty": return 20;
            case "tees": case "thirty": return 30;
            case "chalis": case "forty": return 40;
            case "pachaas": case "fifty": return 50;
            default: return 1;
        }
    }
}
