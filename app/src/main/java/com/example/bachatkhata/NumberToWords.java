package com.example.bachatkhata;

public class NumberToWords {
    private static final String[] units = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
        "Eighteen", "Nineteen"
    };

    private static final String[] tens = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public static String convert(long number) {
        if (number == 0) {
            return "Zero";
        }
        if (number < 0) {
            return "Minus " + convert(-number);
        }

        String words = "";

        if ((number / 10000000) > 0) {
            words += convert(number / 10000000) + " Crore ";
            number %= 10000000;
        }

        if ((number / 100000) > 0) {
            words += convert(number / 100000) + " Lakh ";
            number %= 100000;
        }

        if ((number / 1000) > 0) {
            words += convert(number / 1000) + " Thousand ";
            number %= 1000;
        }

        if ((number / 100) > 0) {
            words += convert(number / 100) + " Hundred ";
            number %= 100;
        }

        if (number > 0) {
            if (!words.equals("")) {
                words += "and ";
            }

            if (number < 20) {
                words += units[(int) number];
            } else {
                words += tens[(int) (number / 10)];
                if ((number % 10) > 0) {
                    words += " " + units[(int) (number % 10)];
                }
            }
        }

        return words.trim();
    }
}
