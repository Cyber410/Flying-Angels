package com.flyingangels.backend.athlete;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;

/** Inclusive whole-year ages. A birthday changes eligibility on the birthday itself. */
public record AgeRange(Integer minAge, Integer maxAge) {
    public AgeRange {
        if ((minAge != null && (minAge < 0 || minAge > 150))
                || (maxAge != null && (maxAge < 0 || maxAge > 150))
                || (minAge != null && maxAge != null && minAge > maxAge))
            throw new IllegalArgumentException("Age bounds must be between 0 and 150, with minAge <= maxAge");
    }

    public boolean active() { return minAge != null || maxAge != null; }

    public static Integer ageOn(LocalDate birthDate, LocalDate referenceDate) {
        return birthDate == null || referenceDate == null || birthDate.isAfter(referenceDate)
                ? null : Period.between(birthDate,referenceDate).getYears();
    }

    /** Date bounds allow directory age filtering to execute in the database. */
    public BirthDates birthDates(LocalDate referenceDate) {
        if (referenceDate == null) throw new IllegalArgumentException("ageAsOf is required when filtering athletes by age");
        return new BirthDates(maxAge == null ? null : referenceDate.minusYears(maxAge + 1L),
                referenceDate.minusYears(minAge == null ? 0 : minAge));
    }
    public record BirthDates(LocalDate afterExclusive, LocalDate throughInclusive) {}

    /** Each result can belong to a different meet, so its reference date is a query expression. */
    public Predicate predicate(CriteriaBuilder cb, Expression<LocalDate> birthDate, Expression<LocalDate> referenceDate) {
        if (!active()) return cb.conjunction();
        var month = cb.function("month",Integer.class,referenceDate);
        var birthMonth = cb.function("month",Integer.class,birthDate);
        var beforeBirthday = cb.or(cb.lessThan(month,birthMonth), cb.and(cb.equal(month,birthMonth),
                cb.lessThan(cb.function("day",Integer.class,referenceDate),cb.function("day",Integer.class,birthDate))));
        var years = cb.diff(cb.function("year",Integer.class,referenceDate),cb.function("year",Integer.class,birthDate));
        var age = cb.diff(years,cb.<Integer>selectCase().when(beforeBirthday,1).otherwise(0));
        var conditions = new ArrayList<Predicate>();
        conditions.add(cb.isNotNull(birthDate));
        conditions.add(cb.isNotNull(referenceDate));
        conditions.add(cb.lessThanOrEqualTo(birthDate,referenceDate));
        if (minAge != null) conditions.add(cb.ge(age,minAge));
        if (maxAge != null) conditions.add(cb.le(age,maxAge));
        return cb.and(conditions.toArray(Predicate[]::new));
    }
}
