package com.realtimechat.common.web;

import com.realtimechat.common.error.UnauthenticatedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects the authenticated {@link AuthenticatedUser} into controller methods.
 *
 * <p>Controllers declare {@code AuthenticatedUser currentUser} instead of touching
 * {@code SecurityContextHolder} directly, which keeps them testable and makes it obvious where the
 * identity comes from. Missing authentication is a hard failure rather than a {@code null}
 * argument, so an endpoint can never accidentally run unauthenticated.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthenticatedException("Authentication is required for this request");
        }
        return user;
    }
}
