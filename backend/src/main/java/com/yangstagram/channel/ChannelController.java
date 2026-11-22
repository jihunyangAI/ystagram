package com.yangstagram.channel;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.channel.ChannelService.ChannelMessage;
import com.yangstagram.channel.ChannelService.ChannelSummary;
import com.yangstagram.channel.ChannelService.ChannelView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** 브로드캐스트 채널 */
@RestController
@RequestMapping("/api")
public class ChannelController {

    private final ChannelService channels;

    public ChannelController(ChannelService channels) {
        this.channels = channels;
    }

    public record CreateRequest(String name) {
    }

    @GetMapping("/channels")
    public List<ChannelSummary> mine() {
        return channels.mine(CurrentUser.requireId());
    }

    @PostMapping("/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelSummary create(@RequestBody CreateRequest req) {
        return channels.create(CurrentUser.requireId(), req.name());
    }

    /** 프로필의 채널 (없으면 204) */
    @GetMapping("/users/{username}/channel")
    public ResponseEntity<ChannelSummary> ofUser(@PathVariable String username) {
        ChannelSummary c = channels.ofUser(username, CurrentUser.requireId());
        return c == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(c);
    }

    @GetMapping("/channels/{id}")
    public ChannelView view(@PathVariable long id, @RequestParam(required = false) Long before) {
        return channels.view(id, CurrentUser.requireId(), before);
    }

    @DeleteMapping("/channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        channels.delete(id, CurrentUser.requireId());
    }

    @PutMapping("/channels/{id}/members/me")
    public ChannelSummary join(@PathVariable long id) {
        return channels.join(id, CurrentUser.requireId());
    }

    @DeleteMapping("/channels/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable long id) {
        channels.leave(id, CurrentUser.requireId());
    }

    /** @param pollOptions 투표 보기 2~4개 (있으면 body가 질문) */
    public record PostRequest(String body, List<String> pollOptions) {
    }

    @PostMapping("/channels/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelMessage post(@PathVariable long id, @RequestBody PostRequest req) {
        return channels.post(id, CurrentUser.requireId(), req.body(), req.pollOptions());
    }

    @PostMapping(value = "/channels/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelMessage postImage(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return channels.postImage(id, CurrentUser.requireId(), file);
    }

    @DeleteMapping("/channels/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable long messageId) {
        channels.deleteMessage(messageId, CurrentUser.requireId());
    }

    public record ReactRequest(String emoji) {
    }

    @PutMapping("/channels/messages/{messageId}/reaction")
    public ChannelMessage react(@PathVariable long messageId, @RequestBody ReactRequest req) {
        return channels.react(messageId, CurrentUser.requireId(), req.emoji());
    }

    @DeleteMapping("/channels/messages/{messageId}/reaction")
    public ChannelMessage unreact(@PathVariable long messageId) {
        return channels.react(messageId, CurrentUser.requireId(), null);
    }

    public record VoteRequest(int option) {
    }

    @PutMapping("/channels/messages/{messageId}/vote")
    public ChannelMessage vote(@PathVariable long messageId, @RequestBody VoteRequest req) {
        return channels.vote(messageId, CurrentUser.requireId(), req.option());
    }
}
