package com.maxkapral.tests;

import com.maxkapral.annotations.Unionize;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

@Unionize(types = {
        Date.class,
        Instant.class,
        ZonedDateTime.class,
        OffsetDateTime.class,
        LocalDateTime.class,
        LocalDate.class,
        LocalTime.class,
        Calendar.class
}, names = {
        "date",
        "instant",
        "zoned",
        "offset",
        "localdatetime",
        "localdate",
        "localtime",
        "calendar"
})
interface Timeline {
}
