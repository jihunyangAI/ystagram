package com.yangstagram.common.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 앱 전체가 쓰는 가상 시계.
 * 평소에는 실제 시간과 같고, Lab에서 오프셋을 주면 시간을 앞당겨 볼 수 있다
 * (스토리 24시간 만료, 15분 수정 제한 같은 규칙 확인용).
 * 시간 계산은 반드시 {@code Instant.now(clock)}처럼 이 시계를 주입받아 쓴다.
 */
public class LabClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public LabClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private LabClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    public Duration getOffset() {
        return offset.get();
    }

    public void setOffset(Duration newOffset) {
        offset.set(newOffset);
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new LabClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
