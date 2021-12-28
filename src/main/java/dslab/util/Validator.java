package dslab.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validator {

    private static final String EMAIL_PATTERN = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$";
    private static final String IS_INTEGER_PATTERN = "-?\\d+";

    public static boolean isValidEmail(String email) {
        Pattern pattern = Pattern.compile(EMAIL_PATTERN, Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(email);
        return matcher.find();
    }

    public static boolean isValidDomain(String email, Map<String, String> domains) {
        String[] e = email.split("@");
        return (e.length == 2 && domains.containsKey(e[1]));
    }

    public static boolean isInteger(String s) {
        return s.matches(IS_INTEGER_PATTERN);
    }

}
