package com.yangstagram.common;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 배포용: 화면(React 빌드 결과, frontend/dist)을 이 서버가 같은 주소로 내보낸다.
 * 개발 중에는 Vite가 화면을 맡으므로 yangstagram.web-dir이 비어 있으면 아무것도 하지 않는다.
 * 화면 주소(/p/abc, /direct 등)는 파일이 아니므로 index.html을 돌려주고, 경로 처리는 React Router가 한다.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Value("${yangstagram.web-dir:}")
    private String webDir;

    /** 첫 화면(/)은 경로가 비어 있어 리소스 처리기가 받지 않으므로 index.html로 넘긴다 */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (!webDir.isBlank()) registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (webDir.isBlank()) return;
        String location = Path.of(webDir).toAbsolutePath().normalize().toUri().toString();
        Resource index = new FileSystemResource(Path.of(webDir, "index.html"));
        // 빌드 파일 이름에 내용 해시가 붙는 assets/는 오래 캐시, 나머지(index.html)는 매번 확인
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/")
                .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic());
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource base) throws IOException {
                        Resource file = super.getResource(path, base);
                        if (file != null) return file;
                        // API·실시간·미디어의 없는 주소는 404 그대로
                        if (path.startsWith("api/") || path.startsWith("ws") || path.startsWith("media/")) return null;
                        return index;
                    }
                });
    }
}
