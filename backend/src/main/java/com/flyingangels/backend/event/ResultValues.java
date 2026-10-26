package com.flyingangels.backend.event;

import java.math.BigDecimal;
import java.util.Locale;

/** Normalizes measurements without interpreting unconfirmed source suffixes. */
public final class ResultValues {
    private ResultValues() {}
    public record Measurement(BigDecimal score, ResultUnit unit, ResultStatus status) {}
    public static ResultUnit unitFor(String name, RankingOrder order) {
        ResultUnit recognized = recognizedUnit(name);
        if (recognized != null) return recognized;
        return order == RankingOrder.DESC ? ResultUnit.METRES : ResultUnit.SECONDS;
    }
    /** Use the same event recognition for spreadsheet columns and stored result units. */
    public static boolean isPerformanceEvent(String name) {
        return recognizedUnit(name) != null;
    }
    private static ResultUnit recognizedUnit(String name) {
        String n=name.trim().toLowerCase(Locale.ROOT);
        if (n.contains("point") || n.contains("athlon")) return ResultUnit.POINTS;
        if (n.contains("jump") || n.contains("put") || n.contains("discus") || n.contains("javelin")
                || n.contains("hammer") || n.contains("throw") || n.contains("vault")) return ResultUnit.METRES;
        if (n.matches("(?:\\d+\\s*[x×]\\s*)?\\d+\\s*m(?:\\s+(?:run|race))?")
                || n.contains("hurdle") || n.contains("mile") || n.contains("steeple")
                || n.contains("sprint") || n.contains("relay") || n.contains("race")) return ResultUnit.SECONDS;
        return null;
    }
    public static ResultStatus status(String raw) {
        try { return ResultStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return ResultStatus.VALID; }
    }
    public static Measurement parse(String raw, ResultUnit unit, BigDecimal supplied) {
        if (raw==null || raw.isBlank()) throw new IllegalArgumentException("Result is required");
        ResultStatus status=status(raw);
        if (status!=ResultStatus.VALID) {
            if (status==ResultStatus.PENDING || status==ResultStatus.UNPARSED) throw new IllegalArgumentException("Unsupported performance status");
            if (supplied!=null) throw new IllegalArgumentException("Non-performance results must have null score");
            return new Measurement(null,unit,status);
        }
        String text=raw.trim();
        if (unit==ResultUnit.METRES) text=text.replaceFirst("(?i)\\s*m$", "").trim();
        BigDecimal value;
        try {
            if (text.contains(":")) {
                if (unit!=ResultUnit.SECONDS || !text.matches("\\d+:[0-5]\\d(?:\\.\\d+)?")) throw new NumberFormatException();
                String[] parts=text.split(":");
                value=new BigDecimal(parts[0]).multiply(BigDecimal.valueOf(60)).add(new BigDecimal(parts[1]));
            } else {
                if (!text.matches("\\d+(?:\\.\\d+)?")) throw new NumberFormatException();
                value=new BigDecimal(text);
            }
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Unrecognized result format: " + raw); }
        if (value.precision()-value.scale()>8 || value.scale()>4) throw new IllegalArgumentException("Result exceeds supported precision");
        if (supplied!=null && value.compareTo(supplied)!=0) throw new IllegalArgumentException("Score must match normalized result value");
        return new Measurement(value,unit,ResultStatus.VALID);
    }
}
