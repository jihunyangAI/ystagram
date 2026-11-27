package com.yangstagram.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 배포용 파일 저장소: Supabase Storage (공개 버킷).
 * 올리기는 비밀 키로 REST 업로드, 보기는 /media/** → 공개 주소로 넘김 (MediaWebConfig).
 */
@Component
public class RemoteMedia {

    private final MediaProperties.Remote remote;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RemoteMedia(MediaProperties props) {
        this.remote = props.remote();
    }

    /** @param key 버킷 안의 경로 (예: u/2026/11/abc.jpg) */
    public void upload(String key, HttpRequest.BodyPublisher body, String contentType) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(remote.uploadBase() + key));
        // 새 형식 비밀 키(sb_secret_…)는 JWT가 아니라 apikey 머리글로만, 예전 service_role 키(JWT)는 둘 다
        if (!remote.key().startsWith("sb_")) b.header("Authorization", "Bearer " + remote.key());
        HttpRequest req = b
                .timeout(Duration.ofMinutes(5))
                .header("apikey", remote.key())
                .header("Content-Type", contentType)
                .header("Cache-Control", "max-age=2592000")
                .POST(body)
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("파일 저장소 업로드 실패 " + res.statusCode() + ": " + res.body());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
