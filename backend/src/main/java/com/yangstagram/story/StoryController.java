package com.yangstagram.story;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.common.paging.Slice;
import com.yangstagram.dm.DmDtos.MessageView;
import com.yangstagram.story.StoryService.ReplyRequest;
import com.yangstagram.story.StoryService.StoryView;
import com.yangstagram.story.StoryService.TrayItem;
import com.yangstagram.story.StoryService.Viewer;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService stories;

    public StoryController(StoryService stories) {
        this.stories = stories;
    }

    @GetMapping("/tray")
    public List<TrayItem> tray() {
        return stories.tray(CurrentUser.requireId());
    }

    @GetMapping("/users/{username}")
    public List<StoryView> ofUser(@PathVariable String username) {
        return stories.ofUser(CurrentUser.requireId(), username);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StoryView create(@RequestPart("file") MultipartFile file,
                            @RequestPart(value = "altText", required = false) String altText) {
        return stories.create(CurrentUser.requireId(), file, altText);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        stories.delete(CurrentUser.requireId(), id);
    }

    @PostMapping("/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable long id) {
        stories.markSeen(CurrentUser.requireId(), id);
    }

    @PutMapping("/{id}/like")
    public StoryView like(@PathVariable long id) {
        return stories.like(CurrentUser.requireId(), id, true);
    }

    @DeleteMapping("/{id}/like")
    public StoryView unlike(@PathVariable long id) {
        return stories.like(CurrentUser.requireId(), id, false);
    }

    @PostMapping("/{id}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView reply(@PathVariable long id, @RequestBody ReplyRequest req) {
        return stories.reply(CurrentUser.requireId(), id, req.body());
    }

    @GetMapping("/{id}/viewers")
    public List<Viewer> viewers(@PathVariable long id) {
        return stories.viewers(CurrentUser.requireId(), id);
    }

    /** 내 스토리 아카이브 (만료된 것 포함) */
    @GetMapping("/archive")
    public Slice<StoryView> archive(@RequestParam(required = false) Long cursor) {
        return stories.archive(CurrentUser.requireId(), cursor);
    }
}
