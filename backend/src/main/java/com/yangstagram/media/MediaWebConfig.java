package com.yangstagram.media;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /media/u/** → 업로드 폴더, /media/seed/** → 가짜 데이터 사진 폴더 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final MediaProperties props;

    public MediaWebConfig(MediaProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(30)).cachePublic();
        registry.addResourceHandler("/media/u/**")
                .addResourceLocations(dirUri(props.uploadDir()))
                .setCacheControl(cache);
        registry.addResourceHandler("/media/seed/**")
                .addResourceLocations(dirUri(props.seed()))
                .setCacheControl(cache);
        // 가짜 릴스 영상 (gradlew reelClips로 만든 것). 영상은 Range 요청(구간 재생)을 지원해야 한다 — 정적 리소스 처리기가 지원함
        registry.addResourceHandler("/media/reels/**")
                .addResourceLocations(dirUri(props.reels()))
                .setCacheControl(cache);
    }

    /** 폴더가 아직 없어도 끝에 '/'가 붙도록 */
    private static String dirUri(java.nio.file.Path dir) {
        String uri = dir.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
