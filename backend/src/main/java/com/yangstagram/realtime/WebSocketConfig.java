package com.yangstagram.realtime;

import com.yangstagram.auth.AuthUser;
import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 실시간 이벤트 (기획서 §12): STOMP over WebSocket.
 * <ul>
 *   <li>연결: {@code /ws} — 로그인 세션 쿠키가 있어야 한다 (SecurityConfig)</li>
 *   <li>받기: {@code /user/queue/events} 하나로 모든 개인 이벤트({type, data})를 받는다</li>
 *   <li>보내기: {@code /app/typing} (입력 중 표시). 메시지 전송·읽음은 REST로 한다 (CSRF 보호 유지)</li>
 * </ul>
 * 사용자 구분 이름은 사용자 이름이 아니라 id다 (프로필에서 사용자 이름을 바꿔도 연결이 유지되도록).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // 다른 사이트가 로그인 쿠키를 이용해 연결하지 못하도록 로컬 주소만 허용
                .setAllowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler,
                                                      Map<String, Object> attributes) {
                        if (request.getPrincipal() instanceof Authentication auth
                                && auth.getPrincipal() instanceof AuthUser user) {
                            return new UserPrincipal(user.id());
                        }
                        return null;
                    }
                });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /** STOMP 세션의 사용자 (이름 = 사용자 id) */
    public record UserPrincipal(long id) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(id);
        }
    }
}
