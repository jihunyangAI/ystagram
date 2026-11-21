package com.yangstagram.dm;

import com.yangstagram.auth.CurrentUser;
import com.yangstagram.dm.DmDtos.ConversationView;
import com.yangstagram.dm.DmDtos.EditMessage;
import com.yangstagram.dm.DmDtos.InboxItem;
import com.yangstagram.dm.DmDtos.MessagePage;
import com.yangstagram.dm.DmDtos.MessageView;
import com.yangstagram.dm.DmDtos.NewConversation;
import com.yangstagram.dm.DmDtos.ReactRequest;
import com.yangstagram.dm.DmDtos.ReadRequest;
import com.yangstagram.dm.DmDtos.SendMessage;
import com.yangstagram.dm.DmDtos.ShareRequest;
import com.yangstagram.dm.DmDtos.TypingRequest;
import com.yangstagram.dm.DmDtos.Unread;
import com.yangstagram.realtime.WebSocketConfig.UserPrincipal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/direct")
public class DmController {

    private final DmService dm;

    public DmController(DmService dm) {
        this.dm = dm;
    }

    @GetMapping("/inbox")
    public List<InboxItem> inbox(@RequestParam(defaultValue = "PRIMARY") String folder) {
        return dm.inbox(CurrentUser.requireId(), folder);
    }

    @GetMapping("/unread")
    public Unread unread() {
        return dm.unread(CurrentUser.requireId());
    }

    /** 받는 사람 1명이면 1:1 (기존 방 재사용), 여러 명이면 그룹 */
    @PostMapping("/conversations")
    public ConversationView open(@RequestBody NewConversation req) {
        return dm.open(CurrentUser.requireId(), req.usernames(), req.title());
    }

    @GetMapping("/conversations/{id}")
    public ConversationView conversation(@PathVariable long id) {
        return dm.conversation(id, CurrentUser.requireId());
    }

    @PutMapping("/conversations/{id}/accept")
    public ConversationView accept(@PathVariable long id) {
        return dm.accept(id, CurrentUser.requireId());
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable long id) {
        dm.leave(id, CurrentUser.requireId());
    }

    @GetMapping("/conversations/{id}/messages")
    public MessagePage messages(@PathVariable long id, @RequestParam(required = false) Long before) {
        return dm.messages(id, CurrentUser.requireId(), before);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@PathVariable long id, @RequestBody SendMessage req) {
        return dm.send(id, CurrentUser.requireId(), req.body(), req.replyToId());
    }

    @PostMapping(value = "/conversations/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView sendImage(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return dm.sendImage(id, CurrentUser.requireId(), file);
    }

    @PutMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long id, @RequestBody ReadRequest req) {
        dm.markRead(id, CurrentUser.requireId(), req.messageId());
    }

    @PatchMapping("/messages/{id}")
    public MessageView edit(@PathVariable long id, @RequestBody EditMessage req) {
        return dm.edit(id, CurrentUser.requireId(), req.body());
    }

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsend(@PathVariable long id) {
        dm.unsend(id, CurrentUser.requireId());
    }

    @PutMapping("/messages/{id}/reaction")
    public MessageView react(@PathVariable long id, @RequestBody ReactRequest req) {
        return dm.react(id, CurrentUser.requireId(), req.emoji());
    }

    @DeleteMapping("/messages/{id}/reaction")
    public MessageView unreact(@PathVariable long id) {
        return dm.react(id, CurrentUser.requireId(), null);
    }

    /** 게시물을 여러 사람에게 DM으로 공유 */
    @PostMapping("/share")
    public Map<String, Object> share(@RequestBody ShareRequest req) {
        return Map.of("conversations", dm.share(CurrentUser.requireId(), req.shortcode(), req.usernames(), req.body()));
    }

    /** STOMP: /app/typing — 입력 중 표시 (저장하지 않고 바로 전달) */
    @MessageMapping("/typing")
    public void typing(TypingRequest req, Principal principal) {
        if (principal instanceof UserPrincipal user) {
            dm.typing(req.conversationId(), user.id());
        }
    }
}
