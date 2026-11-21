package com.yangstagram.event;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.event.EventService.ClientEvent;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 모아서 보내는 행동 이벤트 (노출·시청 시간·프로필 방문·건너뛰기·관심 없음) */
@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    public record EventBatch(List<ClientEvent> events) {
    }

    @PostMapping("/events")
    public Map<String, Integer> record(@RequestBody EventBatch batch) {
        return Map.of("recorded", events.recordClient(CurrentUser.requireId(), batch.events()));
    }
}
