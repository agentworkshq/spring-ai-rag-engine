package dev.agentworkshq.rag.core.text;

public final class Texts {

    public static final int MAX_ID_LENGTH = 100;
    public static final int MAX_ECHO_LENGTH = 64;

    private Texts() {
    }

    public static boolean isPlain(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.chars().noneMatch(Character::isISOControl);
    }

    public static String clean(String value, int maxLength) {
        StringBuilder result = new StringBuilder();
        value.codePoints()
                .filter(c -> !Character.isISOControl(c) && !isBidiOrLineBreak(c))
                .takeWhile(c -> result.length() + Character.charCount(c) <= maxLength)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static boolean isBidiOrLineBreak(int c) {
        return c == 0x061C || c == 0x200E || c == 0x200F || (c >= 0x2028 && c <= 0x202E)
                || (c >= 0x2066 && c <= 0x2069);
    }
}
