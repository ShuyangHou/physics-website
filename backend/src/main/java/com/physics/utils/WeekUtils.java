package com.physics.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class WeekUtils {

    private WeekUtils() {
    }

    public static LocalDate getWeek1Monday(LocalDate semesterStartDate) {
        if (semesterStartDate == null) {
            return null;
        }
        return semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static int getTotalWeeks(LocalDate semesterStartDate, LocalDate semesterEndDate) {
        if (semesterStartDate == null || semesterEndDate == null) {
            return 0;
        }
        LocalDate week1Monday = getWeek1Monday(semesterStartDate);
        if (week1Monday == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(week1Monday, semesterEndDate);
        if (days < 0) {
            return 0;
        }
        return (int) (days / 7) + 1;
    }
    public static List<Integer> getTeachingWeeksRuleB(LocalDate semesterStartDate, LocalDate semesterEndDate, Integer weekType) {
        int totalWeeks = getTotalWeeks(semesterStartDate, semesterEndDate);
        List<Integer> weeks = new ArrayList<>();
        if (totalWeeks <= 0) {
            return weeks;
        }
        int startWeek = 3;
        for (int w = startWeek; w <= totalWeeks; w++) {
            if (weekType == null || weekType == 0) {
                if (w % 2 == 1) {
                    weeks.add(w);
                }
            } else {
                if (w % 2 == 0) {
                    weeks.add(w);
                }
            }
        }
        return weeks;
    }
}
