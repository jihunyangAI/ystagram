package com.yangstagram.audio;

import com.yangstagram.audio.AudioService.AudioPage;
import com.yangstagram.audio.AudioService.AudioRef;
import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.paging.Cursor;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.post.PostQueries;
import com.yangstagram.post.dto.PostDtos.GridItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 음악 고르기·오디오 페이지("이 오디오 사용") */
@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioService audio;
    private final PostQueries queries;

    public AudioController(AudioService audio, PostQueries queries) {
        this.audio = audio;
        this.queries = queries;
    }

    @GetMapping
    public List<AudioRef> search(@RequestParam(defaultValue = "") String q) {
        CurrentUser.requireId();
        return audio.search(q, 30);
    }

    @GetMapping("/{id}")
    public AudioPage page(@PathVariable long id) {
        CurrentUser.requireId();
        return audio.page(id);
    }

    /** 이 오디오를 쓴 릴스 (볼 수 있는 것만, 최신순) */
    @GetMapping("/{id}/reels")
    public Slice<GridItem> reels(@PathVariable long id, @RequestParam(required = false) String cursor) {
        return queries.audioGrid(id, CurrentUser.requireId(), Cursor.decode(cursor), 18);
    }
}
