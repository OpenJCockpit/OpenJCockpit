package nl.metafactory.agents.definition.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects high-risk identifiers: Dutch BSN (11-proef), IBAN (mod-97) and payment card numbers
 * (Luhn). Returns only the kinds found, never the values.
 */
public final class PiiDetector {

    private static final Pattern BSN = Pattern.compile("(?<!\\d)\\d{9}(?!\\d)");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30}\\b");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");

    private PiiDetector() {
    }

    public static List<String> detect(String text) {
        var kinds = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return kinds;
        }
        if (any(BSN.matcher(text), PiiDetector::isBsn)) {
            kinds.add("bsn");
        }
        if (any(IBAN.matcher(text), m -> isIban(m.replace(" ", "")))) {
            kinds.add("iban");
        }
        if (any(CARD.matcher(text), m -> isLuhn(m.replaceAll("[ -]", "")))) {
            kinds.add("payment-card");
        }
        return kinds;
    }

    private static boolean any(Matcher matcher, java.util.function.Predicate<String> check) {
        while (matcher.find()) {
            if (check.test(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    static boolean isBsn(String s) {
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (9 - i) * (s.charAt(i) - '0');
        }
        sum -= s.charAt(8) - '0';
        return sum % 11 == 0 && !s.equals("000000000");
    }

    static boolean isIban(String s) {
        String rearranged = s.substring(4) + s.substring(0, 4);
        int mod = 0;
        for (char c : rearranged.toCharArray()) {
            int v = Character.isDigit(c) ? c - '0' : c - 'A' + 10;
            mod = v > 9 ? (mod * 100 + v) % 97 : (mod * 10 + v) % 97;
        }
        return mod == 1;
    }

    static boolean isLuhn(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
