package com.yangstagram.dm;

import com.yangstagram.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;

/** DM 응답·요청 모음 */
public final class DmDtos {

    private DmDtos() {
    }

    /** 받은 메시지함 한 줄 */
    public record InboxItem(long id, boolean isGroup, String title, List<UserSummary> members, String folder,
                            LastMessage last, boolean unread, boolean online, Instant lastActiveAt) {
    }

    public record LastMessage(long id, long senderId, String kind, String text, Instant createdAt) {
    }

    public record Unread(long primary, long general, long requests) {
    }

    /** 대화방 머리 정보 */
    public record ConversationView(long id, boolean isGroup, String title, List<Member> members, String folder,
                                   long myLastRead) {
    }

    /** 대화 상대. lastReadMessageId로 "읽음" 표시를 만든다 */
    public record Member(UserSummary user, long lastReadMessageId, boolean online, Instant lastActiveAt) {
    }

    public record MessageView(long id, long conversationId, UserSummary sender, String kind, String body,
                              Media media, SharedPost post, SharedStory story, ReplyTo replyTo,
                              Instant createdAt, boolean edited, boolean unsent, List<Reaction> reactions,
                              boolean canEdit, String quote, BlendRef blend) {
    }

    /** Blend 초대 메시지 (BLEND_INVITE) */
    public record BlendRef(long id, String status) {
    }

    public record Media(String url, int width, int height) {
    }

    /** 공유한 게시물 미리보기. 볼 수 없게 되면 available=false */
    public record SharedPost(String shortcode, String thumbUrl, UserSummary author, String caption,
                             boolean available) {
    }

    /** 스토리 답장 미리보기. 만료되면 expired=true (사진은 보내는 사람·스토리 주인만 계속 보임) */
    public record SharedStory(long id, String mediaUrl, UserSummary author, boolean expired) {
    }

    public record ReplyTo(long id, String senderUsername, String text) {
    }

    public record Reaction(long userId, String emoji) {
    }

    public record MessagePage(List<MessageView> items, Long nextBefore) {
    }

    // ------------------------------------------------------------ 요청

    public record NewConversation(List<String> usernames, String title) {
    }

    public record SendMessage(String body, Long replyToId) {
    }

    public record EditMessage(String body) {
    }

    public record ReadRequest(long messageId) {
    }

    public record ReactRequest(String emoji) {
    }

    public record ShareRequest(String shortcode, List<String> usernames, String body) {
    }

    public record TypingRequest(long conversationId) {
    }

    // ------------------------------------------------------------ 실시간 이벤트

    public record ReadEvent(long conversationId, long userId, long messageId) {
    }

    public record TypingEvent(long conversationId, UserSummary user) {
    }

    public record UnsendEvent(long conversationId, long messageId) {
    }
}
