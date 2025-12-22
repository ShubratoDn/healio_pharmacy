package com.heal.io.util;

import java.math.BigDecimal;

public class NumberToWordConverter {

    private static final String[] units = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] tens = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    public static String convertAmount(BigDecimal amount) {
        if (amount == null)
            return "";

        long wholePart = amount.longValue();
        int decimalPart = amount.remainder(BigDecimal.ONE).multiply(new BigDecimal(100)).intValue();

        String result = convert(wholePart) + " Taka";

        if (decimalPart > 0) {
            result += " and " + convert(decimalPart) + " Paisa";
        }

        return result + " Only";
    }

    private static String convert(long n) {
        if (n == 0)
            return "Zero";
        if (n < 0)
            return "Minus " + convert(-n);

        String result = "";

        if (n >= 10000000) { // Crore
            result += convert(n / 10000000) + " Crore ";
            n %= 10000000;
        }

        if (n >= 100000) { // Lakh
            result += convert(n / 100000) + " Lakh ";
            n %= 100000;
        }

        if (n >= 1000) {
            result += convert(n / 1000) + " Thousand ";
            n %= 1000;
        }

        if (n >= 100) {
            result += convert(n / 100) + " Hundred ";
            n %= 100;
        }

        if (n > 0) {
            if (n < 20) {
                result += units[(int) n];
            } else {
                result += tens[(int) (n / 10)];
                if ((n % 10) > 0) {
                    result += "-" + units[(int) (n % 10)];
                }
            }
        }

        return result.trim();
    }
}
