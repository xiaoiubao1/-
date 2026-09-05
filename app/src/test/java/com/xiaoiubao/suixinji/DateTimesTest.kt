package com.xiaoiubao.suixinji

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class DateTimesTest {
    @Test fun pickerRoundTripsCalendarDatesInEastAndWestTimezones() {
        for (zoneName in listOf("Asia/Shanghai", "Asia/Tokyo", "America/Los_Angeles", "Pacific/Honolulu")) {
            val zone = ZoneId.of(zoneName)
            for (date in listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))) {
                val local = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val utc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                assertEquals(utc, DateTimes.pickerDate(local, zone))
                assertEquals(local, DateTimes.localDate(utc, zone))
            }
        }
    }

    @Test fun minimumHashStillSelectsAValidCourseColor() {
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        assertTrue(DateTimes.colorIndex("polygenelubricants", 6) in 0..5)
    }

    @Test fun largePortraitAndPanoramaHaveBoundedDecodedDimensions() {
        for ((width, height) in listOf(12000 to 9000, 20000 to 1000, 9000 to 12000)) {
            val sample = ImageLoader.sampleSize(width, height, 1440, 1920)
            assertTrue(width / sample <= 1440)
            assertTrue(height / sample <= 1920)
            assertEquals(0, sample and (sample - 1))
        }
        assertEquals(1, ImageLoader.sampleSize(320, 240, 1440, 1920))
    }
}
