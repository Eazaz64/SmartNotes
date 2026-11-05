package com.eazaz.smartnotes.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculation for notes.
 *
 * <p><b>Line math</b> (Auto and Manual): a line with math before "=" gets its answer.
 * Words are skipped: "egg 10 + beef 10 =" -> "= 20", "rice 50*2 taka =" -> "= 100".
 *
 * <p><b>Group lines</b> (a word, or nothing, before "="): "total =", "remaining =", "=".
 * <ul>
 *   <li>Auto: the app fills in the numbers above, e.g. "total = 10 + 10 = 20".
 *       "+" and "×" words use the lines since the last heading; "−" and "÷" words use the
 *       whole block (back to the last blank line). A finished group line stands in for the
 *       lines it used, so "remaining" does budget − total.</li>
 *   <li>Manual: the user taps numbers to build "total = 10 + 10", then types "=".</li>
 * </ul>
 * Headings end with ":" (or start with "#") and are never counted. Blank lines separate blocks.
 * Each plain line counts its last number.
 */
public final class CalcEngine {

    private CalcEngine() {}

    public enum Mode { AUTO, MANUAL, OFF }

    /** Which words mean which operation, and the symbol for any other word / bare "=". */
    public static final class Config {
        public final Map<String, Character> words;
        public final char defaultOp;

        public Config(Map<String, Character> words, char defaultOp) {
            Map<String, Character> m = new HashMap<>();
            for (Map.Entry<String, Character> e : words.entrySet()) {
                m.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue());
            }
            this.words = m;
            this.defaultOp = defaultOp;
        }

        public static Map<String, Character> defaultWords() {
            Map<String, Character> m = new LinkedHashMap<>();
            for (String w : new String[]{"total", "sum", "মোট", "যোগ", "যোগফল"}) m.put(w, '+');
            for (String w : new String[]{"remaining", "remain", "subtract", "left", "balance",
                    "বাকি", "অবশিষ্ট"}) m.put(w, '-');
            return m;
        }

        public static Config defaults() { return new Config(defaultWords(), '+'); }
    }

    /** A counted number in the output text (start inclusive, end exclusive). */
    public static final class Highlight {
        public final int start, end;
        public final boolean excluded;
        public final String key;
        Highlight(int start, int end, boolean excluded, String key) {
            this.start = start; this.end = end; this.excluded = excluded; this.key = key;
        }
    }

    public static final class Output {
        public final String text;
        public final int cursor;
        public final List<Highlight> highlights;
        Output(String text, int cursor, List<Highlight> highlights) {
            this.text = text; this.cursor = cursor; this.highlights = highlights;
        }
    }

    // ------------------------------------------------------------------ patterns

    private static final String D = "0-9০-৯";
    // After "=": nothing, "?", or an answer with an optional unit (kept), e.g. "100 tk".
    private static final Pattern ANSWER = Pattern.compile(
            "^(?:\\?|(-?[" + D + "][" + D + ".,]*)(\\s*[A-Za-z°²³/঳ঀ-৥ৰ-৿]+[0-9]?)?)?$");
    // Auto group line body written by the app: "10 + 10 = 20" (answer may be missing).
    private static final Pattern FORMULA = Pattern.compile(
            "^\\s*-?[" + D + "][" + D + ".,]*(?:\\s*[-+*/×÷]\\s*-?[" + D + "][" + D + ".,]*)*\\s*=\\s*(?:-?[" + D + "][" + D + ".,]*)?\\s*$");
    private static final Pattern CONVERT = Pattern.compile(
            "(?<![" + D + ".,])(-?[" + D + "][" + D + ".,]*)\\s*([A-Za-z°²³/]+[0-9]?)\\s+(?:to|in|into|->)\\s+([A-Za-z°²³/]+[0-9]?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADJACENT_NUMBERS = Pattern.compile("[" + D + "][.,]?\\s+[.]?[" + D + "]");
    private static final String OPERATORS = "+-*/×÷xX^%";

    // ------------------------------------------------------------------ main entry

    private static final class Entry {
        final int line;
        final double value;
        int start, end;          // number position inside the (new) line
        final int heading;
        final String key;
        final boolean bangla;
        boolean consumed;
        Entry(int line, double value, int start, int end, int heading, String key, boolean bangla) {
            this.line = line; this.value = value; this.start = start; this.end = end;
            this.heading = heading; this.key = key; this.bangla = bangla;
        }
    }

    /** Auto mode with default words (used by tests and checklist items). */
    public static Output process(String text, int cursor, boolean protectCursorLine) {
        return process(text, cursor, protectCursorLine, Mode.AUTO, Config.defaults(), Collections.<String>emptySet());
    }

    /**
     * Recalculates the whole text.
     *
     * @param protectCursorLine true right after a deletion: the cursor's line is not rewritten if the
     *                          cursor is after its "=", so the user can erase an answer.
     * @param excluded          keys of lines the user removed from calculations
     */
    public static Output process(String text, int cursor, boolean protectCursorLine,
                                 Mode mode, Config cfg, Set<String> excluded) {
        List<Highlight> none = new ArrayList<>();
        if (mode == Mode.OFF) return new Output(text, cursor, none);

        String[] lines = text.split("\n", -1);
        String[] keys = lineKeys(lines);
        String[] out = new String[lines.length];
        List<Entry> block = new ArrayList<>();
        List<Object[]> hl = new ArrayList<>(); // {line, start, end, excluded, key}
        int heading = 0;

        int pos = 0, shift = 0, newCursor = cursor;
        boolean changed = false;

        for (int i = 0; i < lines.length; i++) {
            String L = lines[i];
            int lineEnd = pos + L.length();
            boolean cursorHere = cursor >= pos && cursor <= lineEnd;
            int firstEq = L.indexOf('=');
            boolean cursorAfterEq = cursorHere && firstEq >= 0 && cursor - pos > firstEq;
            boolean frozen = protectCursorLine && cursorAfterEq;

            String newL = L;
            Entry own = null;
            String trimmed = L.trim();

            if (trimmed.isEmpty()) {
                block.clear();
                heading++;
            } else if (trimmed.endsWith(":") || trimmed.startsWith("#")) {
                heading++;
            } else if (firstEq < 0) {
                own = valueEntry(L, i, heading, keys[i]);
            } else {
                int eqCount = count(L, '=');
                String label = L.substring(0, firstEq);
                String rest = L.substring(firstEq + 1);
                String conv = eqCount == 1 ? convert(label) : null;

                if (conv != null) {
                    if (ANSWER.matcher(rest.trim()).matches()) {
                        newL = label + "= " + conv + trailingWs(rest);
                    }
                } else if (isMathLike(label)) {
                    if (eqCount == 1) {
                        Matcher m = ANSWER.matcher(rest.trim());
                        if (m.matches()) {
                            Double v = evaluateText(label);
                            if (v == null) {
                                if (!rest.trim().isEmpty()) newL = label + "=" + trailingWs(rest);
                            } else {
                                String ans = format(v);
                                boolean bn = containsBanglaDigit(label);
                                if (bn) ans = toBanglaDigits(ans);
                                String unit = m.group(2) == null ? "" : m.group(2);
                                newL = label + "= " + ans + unit + trailingWs(rest);
                                int s = label.length() + 2;
                                own = new Entry(i, v, s, s + ans.length(), heading, keys[i], bn);
                            }
                        }
                    }
                } else if (mode == Mode.AUTO) {
                    char op = opFor(label, cfg);
                    boolean keyword = isKeyword(label, cfg);
                    String r = rest.trim();
                    boolean writable = r.isEmpty() || r.equals("?") || FORMULA.matcher(rest).matches()
                            || (keyword && ANSWER.matcher(r).matches());
                    if (writable) {
                        boolean sinceHeading = op == '+' || op == '*';
                        List<Entry> inc = new ArrayList<>();
                        List<Entry> exc = new ArrayList<>();
                        for (Entry e : block) {
                            if (e.consumed || (sinceHeading && e.heading != heading)) continue;
                            if (excluded.contains(e.key)) exc.add(e); else inc.add(e);
                        }
                        for (Entry e : exc) hl.add(new Object[]{e.line, e.start, e.end, true, e.key});
                        Double v = inc.isEmpty() ? null : fold(inc, op);
                        if (v != null) {
                            boolean bn = false;
                            StringBuilder f = new StringBuilder();
                            for (int k = 0; k < inc.size(); k++) {
                                Entry e = inc.get(k);
                                if (e.bangla) bn = true;
                                if (k > 0) f.append(' ').append(opSymbol(op)).append(' ');
                                f.append(format(e.value));
                            }
                            String ans = format(v);
                            String body = (inc.size() >= 2 || !keyword) ? f + " = " + ans : ans;
                            if (bn) { body = toBanglaDigits(body); ans = toBanglaDigits(ans); }
                            newL = label + "= " + body;
                            for (Entry e : inc) {
                                e.consumed = true;
                                hl.add(new Object[]{e.line, e.start, e.end, false, e.key});
                            }
                            own = new Entry(i, v, newL.length() - ans.length(), newL.length(), heading, keys[i], bn);
                        } else if (!exc.isEmpty() && !r.isEmpty() && !r.equals("?")) {
                            newL = label + "=";   // everything was removed by the user
                        } else if (exc.isEmpty()) {
                            own = valueEntry(L, i, heading, keys[i]);  // nothing above: keep user's value
                        }
                    } else {
                        own = valueEntry(L, i, heading, keys[i]);  // e.g. "Budget = 500"
                    }
                } else { // MANUAL
                    int lastEq = L.lastIndexOf('=');
                    if (lastEq > firstEq) {
                        String formula = L.substring(firstEq + 1, lastEq);
                        String ansPart = L.substring(lastEq + 1);
                        Matcher m = ANSWER.matcher(ansPart.trim());
                        if (m.matches()) {
                            Double v = evaluateText(formula);
                            if (v != null) {
                                String ans = format(v);
                                boolean bn = containsBanglaDigit(formula);
                                if (bn) ans = toBanglaDigits(ans);
                                String head = L.substring(0, lastEq);
                                newL = head + (head.endsWith(" ") ? "" : " ") + "= " + ans + trailingWs(ansPart);
                                int s = newL.length() - trailingWs(ansPart).length() - ans.length();
                                own = new Entry(i, v, s, s + ans.length(), heading, keys[i], bn);
                            } else if (!ansPart.trim().isEmpty()) {
                                newL = L.substring(0, lastEq) + "=" + trailingWs(ansPart);
                            }
                        }
                    } else if (!rest.trim().isEmpty()) {
                        own = valueEntry(L, i, heading, keys[i]);
                    }
                }
            }

            if (frozen && !newL.equals(L)) {
                newL = L;
                if (own != null) {
                    int[] t = lastNumber(L);
                    if (t == null) own = null; else { own.start = t[0]; own.end = t[1]; }
                }
            }
            if (own != null) block.add(own);

            if (!newL.equals(L)) {
                changed = true;
                if (cursorHere) newCursor = cursorAfterEq ? pos + shift + newL.length() : cursor + shift;
                shift += newL.length() - L.length();
            } else if (cursorHere) {
                newCursor = cursor + shift;
            }
            out[i] = newL;
            pos = lineEnd + 1;
        }

        StringBuilder sb = new StringBuilder();
        int[] starts = new int[out.length];
        for (int i = 0; i < out.length; i++) {
            starts[i] = sb.length();
            sb.append(out[i]);
            if (i < out.length - 1) sb.append('\n');
        }
        String result = changed ? sb.toString() : text;
        List<Highlight> highlights = new ArrayList<>();
        for (Object[] h : hl) {
            int line = (Integer) h[0];
            highlights.add(new Highlight(starts[line] + (Integer) h[1], starts[line] + (Integer) h[2], (Boolean) h[3], (String) h[4]));
        }
        newCursor = Math.max(0, Math.min(newCursor, result.length()));
        return new Output(result, changed ? newCursor : cursor, highlights);
    }

    /** Single line (checklist item) — line math and conversions only. */
    public static String processLine(String line) {
        Output o = process(line, line.length(), false, Mode.MANUAL, Config.defaults(), Collections.<String>emptySet());
        return o.text.equals(line) ? null : o.text;
    }

    // ------------------------------------------------------------------ manual mode helpers

    /** True if line {@code lineIdx} is a group line waiting for numbers ("total =" with nothing after). */
    public static boolean isManualStart(String text, int lineIdx) {
        String[] lines = text.split("\n", -1);
        if (lineIdx < 0 || lineIdx >= lines.length) return false;
        String L = lines[lineIdx];
        int eq = L.indexOf('=');
        if (eq < 0 || count(L, '=') != 1) return false;
        String label = L.substring(0, eq);
        if (isMathLike(label) || convert(label) != null) return false;
        return L.substring(eq + 1).trim().isEmpty();
    }

    /** True if the line is still being built (one "=" only, nothing after a final "="). */
    public static boolean isManualOpen(String text, int lineIdx) {
        String[] lines = text.split("\n", -1);
        if (lineIdx < 0 || lineIdx >= lines.length) return false;
        String L = lines[lineIdx];
        int eq = L.indexOf('=');
        return eq >= 0 && count(L, '=') == 1 && !isMathLike(L.substring(0, eq));
    }

    /** Adds a tapped number to the line being built, putting "+" between numbers. */
    public static Output manualAppend(String text, int lineIdx, String number) {
        String[] lines = text.split("\n", -1);
        String L = lines[lineIdx];
        String t = rtrim(L);
        char last = t.isEmpty() ? ' ' : t.charAt(t.length() - 1);
        String add;
        if (last == '=' || last == '(') add = " " + number;
        else if (isDigit(last) || last == ')') add = " + " + number;
        else add = " " + number; // after an operator the user typed
        lines[lineIdx] = t + add;
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i == lineIdx) cursor = sb.length();
            if (i < lines.length - 1) sb.append('\n');
        }
        return new Output(sb.toString(), cursor, new ArrayList<Highlight>());
    }

    /** Range [start, end) of the number at {@code offset}, or null. */
    public static int[] numberAt(String text, int offset) {
        int p = offset;
        if (p < text.length() && isDigit(text.charAt(p))) { /* inside */ }
        else if (p > 0 && p - 1 < text.length() && isDigit(text.charAt(p - 1))) p = p - 1;
        else return null;
        int s = p, e = p;
        while (s > 0 && isNumChar(text.charAt(s - 1))) s--;
        while (e < text.length() && isNumChar(text.charAt(e))) e++;
        while (s < e && !isDigit(text.charAt(s))) s++;
        while (e > s && !isDigit(text.charAt(e - 1))) e--;
        return s < e ? new int[]{s, e} : null;
    }

    /** Index of the line containing {@code offset}. */
    public static int lineOf(String text, int offset) {
        int line = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    // ------------------------------------------------------------------ classification helpers

    private static Entry valueEntry(String L, int line, int heading, String key) {
        if (convertLike(L)) return null;
        int[] t = lastNumber(L);
        if (t == null) return null;
        Double v = evaluate(L.substring(t[0], t[1]));
        if (v == null) return null;
        return new Entry(line, v, t[0], t[1], heading, key, containsBanglaDigit(L.substring(t[0], t[1])));
    }

    private static boolean convertLike(String L) {
        int eq = L.indexOf('=');
        return convert(eq >= 0 ? L.substring(0, eq) : L) != null;
    }

    /** Keys identify lines for exclusion; numbers are ignored so editing a price keeps it excluded. */
    static String[] lineKeys(String[] lines) {
        String[] keys = new String[lines.length];
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String L = lines[i];
            int eq = L.indexOf('=');
            String base = (eq >= 0 && !isMathLike(L.substring(0, eq))) ? L.substring(0, eq) : L;
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < base.length(); k++) {
                char c = base.charAt(k);
                if (isNumChar(c) || Character.isWhitespace(c) || c == '=') continue;
                b.append(Character.toLowerCase(c));
            }
            String k = b.toString();
            int n = seen.containsKey(k) ? seen.get(k) : 0;
            seen.put(k, n + 1);
            keys[i] = k + "#" + n;
        }
        return keys;
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            if (isWordChar(s.charAt(i))) {
                int j = i;
                while (j < s.length() && isWordChar(s.charAt(j))) j++;
                out.add(s.substring(i, j).toLowerCase(Locale.ROOT));
                i = j;
            } else i++;
        }
        return out;
    }

    private static char opFor(String label, Config cfg) {
        List<String> w = words(label);
        for (int i = w.size() - 1; i >= 0; i--) {
            Character op = cfg.words.get(w.get(i));
            if (op != null) return op;
        }
        return cfg.defaultOp;
    }

    private static boolean isKeyword(String label, Config cfg) {
        for (String w : words(label)) if (cfg.words.containsKey(w)) return true;
        return false;
    }

    private static Double fold(List<Entry> es, char op) {
        double v = es.get(0).value;
        for (int i = 1; i < es.size(); i++) {
            double x = es.get(i).value;
            switch (op) {
                case '-': v -= x; break;
                case '*': v *= x; break;
                case '/': if (x == 0) return null; v /= x; break;
                default: v += x;
            }
        }
        return v;
    }

    private static String opSymbol(char op) {
        switch (op) {
            case '*': return "×";
            case '/': return "÷";
            default: return String.valueOf(op);
        }
    }

    // ------------------------------------------------------------------ expressions

    /** True if the text has a number and an operator once words are removed. */
    static boolean isMathLike(String label) {
        String s = trimLeadingJunk(stripWords(label));
        boolean hasDigit = false, hasOp = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (isDigit(c)) hasDigit = true;
            else if (OPERATORS.indexOf(c) >= 0 && !(k == 0 && c == '-')) hasOp = true;
        }
        return hasDigit && hasOp;
    }

    /** Evaluates the math in a piece of text, skipping words ("egg 10 + beef 10" = 20). */
    static Double evaluateText(String label) {
        String s = trimLeadingJunk(stripWords(label)).trim();
        if (!s.isEmpty() && !ADJACENT_NUMBERS.matcher(s).find()) {
            Double v = evaluate(s);
            if (v != null) return v;
        }
        String suffix = extractExpression(label);   // fallback: math at the end only
        return suffix == null ? null : evaluate(suffix);
    }

    /** Replaces words, currency signs and other symbols by spaces; "x" between numbers means ×. */
    static String stripWords(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (isWordChar(c)) {
                int j = i;
                while (j < n && isWordChar(s.charAt(j))) j++;
                String w = s.substring(i, j);
                boolean times = w.equalsIgnoreCase("x") && prevIsOperand(b) && nextIsOperand(s, j);
                b.append(times ? '*' : ' ');
                i = j;
                continue;
            }
            b.append(isMathChar(c) || c == ' ' ? c : ' ');
            i++;
        }
        return b.toString();
    }

    private static boolean prevIsOperand(StringBuilder b) {
        for (int k = b.length() - 1; k >= 0; k--) {
            char c = b.charAt(k);
            if (c == ' ') continue;
            return isDigit(c) || c == ')';
        }
        return false;
    }

    private static boolean nextIsOperand(String s, int j) {
        for (int k = j; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == ' ') continue;
            return isDigit(c) || c == '(';
        }
        return false;
    }

    private static String trimLeadingJunk(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (isDigit(c) || c == '(' || c == '-' || c == '.') break;
            i++;
        }
        return s.substring(i);
    }

    /** Math at the end of the text (used when words sit between numbers in an odd way). */
    static String extractExpression(String lhs) {
        int start = lhs.length();
        while (start > 0 && isExprChar(lhs.charAt(start - 1))) start--;
        String s = trimLeadingJunk(lhs.substring(start)).trim();
        if (s.isEmpty()) return null;
        boolean hasDigit = false, hasOp = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (isDigit(c)) hasDigit = true;
            else if (OPERATORS.indexOf(c) >= 0 && !(k == 0 && c == '-')) hasOp = true;
        }
        return (hasDigit && hasOp) ? s : null;
    }

    /** Evaluate a bare expression such as "10+10+(5*2)". Null if invalid. */
    public static Double evaluate(String expr) {
        String s = normalize(expr);
        if (s.isEmpty()) return null;
        try {
            double v = new Parser(s).parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normalize(String expr) {
        StringBuilder b = new StringBuilder(expr.length());
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c >= '০' && c <= '৯') c = (char) ('0' + (c - '০'));
            switch (c) {
                case ' ': case ',': continue;
                case '×': case 'x': case 'X': c = '*'; break;
                case '÷': c = '/'; break;
                default: break;
            }
            b.append(c);
        }
        return b.toString();
    }

    private static final class Parser {
        private final String s;
        private int p = 0;

        Parser(String s) { this.s = s; }

        double parse() {
            double v = expr();
            if (p != s.length()) throw new IllegalArgumentException("trailing");
            return v;
        }

        private boolean more() { return p < s.length(); }

        private double expr() {
            double[] t = term();
            double v = t[0];
            while (more()) {
                char c = s.charAt(p);
                if (c != '+' && c != '-') break;
                p++;
                double[] r = term();
                double rv = r[1] == 1 ? v * r[0] : r[0];   // 200 + 10%  => 200 + 20
                v = (c == '+') ? v + rv : v - rv;
            }
            return v;
        }

        private double[] term() {
            double[] f = power();
            double v = f[0];
            boolean pct = f[1] == 1;
            while (more()) {
                char c = s.charAt(p);
                if (c == '*') { p++; v *= power()[0]; }
                else if (c == '/') {
                    p++;
                    double d = power()[0];
                    if (d == 0) throw new ArithmeticException("div0");
                    v /= d;
                } else if (c == '(') { v *= power()[0]; }
                else break;
                pct = false;
            }
            return new double[]{v, pct ? 1 : 0};
        }

        private double[] power() {
            double[] b = unary();
            if (more() && s.charAt(p) == '^') {
                p++;
                double e = power()[0];
                return new double[]{Math.pow(b[0], e), 0};
            }
            return b;
        }

        private double[] unary() {
            if (more() && s.charAt(p) == '-') { p++; double[] r = unary(); return new double[]{-r[0], r[1]}; }
            if (more() && s.charAt(p) == '+') { p++; return unary(); }
            return postfix();
        }

        private double[] postfix() {
            double v = primary();
            boolean pct = false;
            while (more() && s.charAt(p) == '%') { p++; v /= 100.0; pct = true; }
            return new double[]{v, pct ? 1 : 0};
        }

        private double primary() {
            if (!more()) throw new IllegalArgumentException("end");
            char c = s.charAt(p);
            if (c == '(') {
                p++;
                double v = expr();
                if (!more() || s.charAt(p) != ')') throw new IllegalArgumentException("paren");
                p++;
                return v;
            }
            int start = p;
            boolean dot = false;
            while (more()) {
                char d = s.charAt(p);
                if (d >= '0' && d <= '9') p++;
                else if (d == '.' && !dot) { dot = true; p++; }
                else break;
            }
            if (start == p || (p - start == 1 && dot)) throw new IllegalArgumentException("number");
            return Double.parseDouble(s.substring(start, p));
        }
    }

    // ------------------------------------------------------------------ unit conversion

    private static final class Unit {
        final String cat;
        final double factor;
        Unit(String cat, double factor) { this.cat = cat; this.factor = factor; }
    }

    private static final Map<String, Unit> UNITS = new HashMap<>();

    private static void u(String cat, double factor, String... names) {
        for (String n : names) UNITS.put(n.toLowerCase(Locale.ROOT), new Unit(cat, factor));
    }

    static {
        u("len", 0.001, "mm");
        u("len", 0.01, "cm");
        u("len", 1, "m", "meter", "meters", "metre", "metres");
        u("len", 1000, "km", "kilometer", "kilometers");
        u("len", 0.0254, "in", "inch", "inches");
        u("len", 0.3048, "ft", "foot", "feet");
        u("len", 0.9144, "yd", "yard", "yards");
        u("len", 1609.344, "mi", "mile", "miles");
        u("mass", 1e-6, "mg");
        u("mass", 0.001, "g", "gram", "grams");
        u("mass", 1, "kg", "kilo", "kilos");
        u("mass", 1000, "t", "ton", "tons", "tonne");
        u("mass", 0.45359237, "lb", "lbs", "pound", "pounds");
        u("mass", 0.028349523125, "oz", "ounce", "ounces");
        u("mass", 40, "maund", "mon");
        u("area", 1, "m2", "sqm", "m²");
        u("area", 1e6, "km2", "km²");
        u("area", 0.09290304, "ft2", "sqft", "ft²");
        u("area", 10000, "ha", "hectare", "hectares");
        u("area", 4046.8564224, "acre", "acres");
        u("area", 40.468564224, "decimal", "decimals", "shotok");
        u("area", 720 * 0.09290304, "katha", "kata");
        u("area", 14400 * 0.09290304, "bigha");
        u("vol", 0.001, "ml");
        u("vol", 1, "l", "liter", "liters", "litre", "litres");
        u("vol", 1000, "m3", "m³");
        u("vol", 3.785411784, "gal", "gallon", "gallons");
        u("vol", 0.24, "cup", "cups");
        u("speed", 1, "m/s", "mps");
        u("speed", 1 / 3.6, "km/h", "kmh", "kph");
        u("speed", 0.44704, "mph");
        u("speed", 0.514444, "knot", "knots");
        u("time", 1, "s", "sec", "secs", "second", "seconds");
        u("time", 60, "min", "mins", "minute", "minutes");
        u("time", 3600, "h", "hr", "hrs", "hour", "hours");
        u("time", 86400, "day", "days");
        u("time", 604800, "week", "weeks");
        u("time", 31536000, "year", "years", "yr");
        u("data", 1, "b", "byte", "bytes");
        u("data", 1024, "kb");
        u("data", 1024.0 * 1024, "mb");
        u("data", 1024.0 * 1024 * 1024, "gb");
        u("data", 1024.0 * 1024 * 1024 * 1024, "tb");
        u("temp", 0, "c", "°c", "celsius");
        u("temp", 1, "f", "°f", "fahrenheit");
        u("temp", 2, "k", "kelvin");
    }

    static String convert(String lhs) {
        Matcher m = CONVERT.matcher(lhs);
        if (!m.find()) return null;
        Unit from = UNITS.get(m.group(2).toLowerCase(Locale.ROOT));
        Unit to = UNITS.get(m.group(3).toLowerCase(Locale.ROOT));
        if (from == null || to == null || !from.cat.equals(to.cat)) return null;
        Double n = evaluate(m.group(1));
        if (n == null) return null;
        double v;
        if (from.cat.equals("temp")) {
            double c;
            if (from.factor == 0) c = n; else if (from.factor == 1) c = (n - 32) * 5 / 9; else c = n - 273.15;
            if (to.factor == 0) v = c; else if (to.factor == 1) v = c * 9 / 5 + 32; else v = c + 273.15;
        } else {
            v = n * from.factor / to.factor;
        }
        String s = format(v);
        if (containsBanglaDigit(m.group(1))) s = toBanglaDigits(s);
        return s + " " + m.group(3);
    }

    // ------------------------------------------------------------------ small helpers

    static String format(double v) {
        if (Math.abs(v) >= 1e15) return String.valueOf(v);
        BigDecimal bd = new BigDecimal(v).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.compareTo(BigDecimal.ZERO) == 0) return "0";
        return bd.toPlainString();
    }

    private static int[] lastNumber(String L) {
        int e = L.length();
        while (e > 0 && !isDigit(L.charAt(e - 1))) e--;
        if (e == 0) return null;
        int s = e;
        while (s > 0 && isNumChar(L.charAt(s - 1))) s--;
        while (s < e && !isDigit(L.charAt(s))) s++;
        if (s > 0 && L.charAt(s - 1) == '-' && (s == 1 || !isDigit(L.charAt(s - 2)))) {
            if (s >= 2 && (L.charAt(s - 2) == ' ' || L.charAt(s - 2) == '=')) s--;
        }
        return new int[]{s, e};
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String rtrim(String s) {
        int i = s.length();
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) i--;
        return s.substring(0, i);
    }

    private static String trailingWs(String s) {
        if (s.trim().isEmpty()) return "";
        int i = s.length();
        while (i > 0 && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) i--;
        return s.substring(i);
    }

    private static boolean isDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= '০' && c <= '৯');
    }

    private static boolean isNumChar(char c) {
        return isDigit(c) || c == '.' || c == ',';
    }

    private static boolean isMathChar(char c) {
        return isNumChar(c) || c == '(' || c == ')' || "+-*/×÷^%".indexOf(c) >= 0;
    }

    private static boolean isExprChar(char c) {
        return isNumChar(c) || c == ' ' || c == '(' || c == ')' || OPERATORS.indexOf(c) >= 0;
    }

    private static boolean isWordChar(char c) {
        if (isDigit(c)) return false;
        int t = Character.getType(c);
        return Character.isLetter(c) || t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK || c == '_';
    }

    private static boolean containsBanglaDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '০' && c <= '৯') return true;
        }
        return false;
    }

    private static String toBanglaDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) ('০' + (c - '0')) : c);
        }
        return b.toString();
    }
}
