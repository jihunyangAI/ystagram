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
                .addResourceLocations(dirUri(props.seedDir()))
                .setCacheControl(cache);
    }

    /** 폴더가 아직 없어도 끝에 '/'가 붙도록 */
    private static String dirUri(java.nio.file.Path dir) {
        String uri = dir.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
