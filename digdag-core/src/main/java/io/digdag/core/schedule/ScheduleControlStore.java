package io.digdag.core.schedule;

import java.time.Instant;
import io.digdag.spi.ScheduleTime;

public interface ScheduleControlStore
{
    boolean updateNextScheduleTime(int schedId, ScheduleTime nextTime, Instant lastSessionTime);

    boolean updateNextScheduleTime(int schedId, ScheduleTime nextTime);

    boolean disableSchedule(int schedId);

    boolean enableSchedule(int schedId);

    StoredSchedule getSchedule(int schedId);
}
