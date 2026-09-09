package org.houxg.leamonax;


import org.houxg.leamonax.utils.TimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TimeFormatTest {
    private DateTimeZone originalTimeZone;

    @Before
    public void setUpTimeZone() {
        originalTimeZone = DateTimeZone.getDefault();
        DateTimeZone.setDefault(DateTimeZone.forID("Asia/Shanghai"));
    }

    @After
    public void restoreTimeZone() {
        DateTimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void testCompose() throws Exception {
        long time = 1480040991786l;
        String expectTime = "2016-11-25T02:29:51.786Z";
        Assert.assertEquals(expectTime, TimeUtils.toServerTime(time));
    }

    @Test
    public void testParse() throws Exception {
        String[] formatCases = new String[]{
                "2016-11-25T02:29:51.861+08:00",
                "2016-11-25T02:29:51.8612348761+08:00"
        };
        long expectTime = 1480012191861l;
        for (String format : formatCases) {
            Assert.assertEquals("test " + format, expectTime, TimeUtils.toTimestamp(format));
        }

        formatCases = new String[] {
                "2016-11-25T02:29:51+08:00"
        };
        expectTime = 1480012191000l;
        for (String format : formatCases) {
            Assert.assertEquals("test " + format, expectTime, TimeUtils.toTimestamp(format));
        }
    }

    @Test
    public void acceptsLegacySingleDigitTimezoneOffset() {
        Assert.assertEquals(
                TimeUtils.toTimestamp("2016-11-25T02:29:51.861+08:00"),
                TimeUtils.toTimestamp("2016-11-25T02:29:51.861+8:0")
        );
    }
}
