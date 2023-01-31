package com.agora.server.config;

import com.agora.server.auth.filter.JwtAuthenticationFilter;
import com.agora.server.auth.provider.JwtTokenProvider;
import com.agora.server.config.filter.CorsFilterConfig;
import com.agora.server.config.filter.MyFilter;
import com.agora.server.user.oauth.OAuth2AuthenticationFailureHandler;
import com.agora.server.user.oauth.OAuth2AuthenticationSuccessHandler;
import com.agora.server.user.service.PrincipalOauth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsFilterConfig corsFilter;

    @Value("${jwt.secret}")
    private String jwtSecret;
    private final PrincipalOauth2UserService principalOauth2UserService;

    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;


    @Bean
    protected SecurityFilterChain configure(HttpSecurity http) throws Exception {
        http

                .addFilter(corsFilter.corsFilter())
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .csrf()
                .disable()
                .formLogin()
                .disable()
                .httpBasic()
                .disable()
                .authorizeRequests()
                .antMatchers("/api/v1/room/**").authenticated()
                .anyRequest().permitAll()
                .and()
                .oauth2Login()
                .authorizationEndpoint()
                .baseUri("/oauth2/authorization")
                .and()
                .redirectionEndpoint()
                .baseUri("/login/oauth2/code/*")
                .and()
                .userInfoEndpoint()
                .userService(principalOauth2UserService)
                .and()
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .and()
                .addFilterBefore(new JwtAuthenticationFilter(new JwtTokenProvider(jwtSecret)), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MyFilter(), FilterSecurityInterceptor.class);
        return http.build();
    }
}
