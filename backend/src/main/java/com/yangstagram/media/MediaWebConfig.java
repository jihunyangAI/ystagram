package com.yangstagram.media;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * /media/u/** → 업로드 폴더, /media/seed/** → 가짜 데이터 사진 폴더, /media/reels/** → 가짜 릴스 영상.
 * 배포(파일 저장소 사용)에서는 같은 주소를 Supabase 공개 주소로 넘긴다 (DB에 저장된 주소는 그대로).
 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final MediaProperties props;

    public MediaWebConfig(MediaProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (props.remote().enabled()) return;
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

    /** 파일 저장소를 쓸 때만: /media/x/y → 공개 버킷 주소/x/y 로 302 (브라우저가 그 주소를 기억해 두도록 캐시 허용) */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> remoteMediaRedirect() {
        MediaProperties.Remote remote = props.remote();
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String path = req.getRequestURI().substring(req.getContextPath().length() + "/media/".length());
                if (path.startsWith("ph/")) { // 서버가 그리는 그림 (PlaceholderController)
                    chain.doFilter(req, res);
                    return;
                }
                if (path.isEmpty() || path.contains("..")) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                res.setHeader("Cache-Control", "public, max-age=86400");
                res.setStatus(HttpServletResponse.SC_FOUND);
                res.setHeader("Location", remote.publicBase() + path);
            }
        });
        bean.addUrlPatterns("/media/*");
        bean.setEnabled(remote.enabled());
        bean.setOrder(-200); // 보안 필터보다 먼저 (로그인 확인이 필요 없는 공개 파일)
        return bean;
    }

    /** 폴더가 아직 없어도 끝에 '/'가 붙도록 */
    private static String dirUri(java.nio.file.Path dir) {
        String uri = dir.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
