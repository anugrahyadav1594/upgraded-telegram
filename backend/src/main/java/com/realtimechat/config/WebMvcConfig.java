package com.realtimechat.config;

import com.realtimechat.common.ratelimit.RateLimitInterceptor;
import com.realtimechat.common.web.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * MVC wiring: the rate-limit interceptor and the {@code AuthenticatedUser} argument resolver.
 *
 * <p>Both are registered here rather than derived from annotations so the behaviour is explicit and
 * greppable — and so a new controller cannot forget them.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
                        CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
