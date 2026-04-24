package com.smartedu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 扩展配置。
 *
 * <p>
 * 当前只用于暴露 MinerU 解析输出中的图片 / 表格 / 公式渲染图静态资源；
 * 前端通过 {@link #assetUrlPrefix} 开头的 URL 访问 {@link #assetsPath} 下的文件。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${mineru.assets-path:./uploads/mineru-assets}")
    private String assetsPath;

    @Value("${mineru.asset-url-prefix:/api/upload/mineru-assets}")
    private String assetUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absPath = Paths.get(assetsPath).toAbsolutePath().normalize();
        String location = absPath.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        String pattern = assetUrlPrefix.endsWith("/") ? assetUrlPrefix + "**" : assetUrlPrefix + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
