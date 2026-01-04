package com.colombus.common.security.webflux;

import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

import com.colombus.common.security.core.ActuatorSecurityProperties;
import com.colombus.common.security.core.ColombusJwtSupport;
import com.colombus.common.security.core.JwtSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties({JwtSecurityProperties.class, ActuatorSecurityProperties.class})
public class ColombusWebfluxSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder colombusReactivePasswordEncoder() {
        log.info("[ColombusSecurity] Init reactive PasswordEncoder (delegating).");
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean(name = "actuatorReactiveUserDetailsService")
    @ConditionalOnProperty(
        prefix = "security.actuator",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public MapReactiveUserDetailsService actuatorReactiveUserDetailsService(
        ActuatorSecurityProperties props,
        PasswordEncoder encoder
    ) {
        log.info("[ColombusSecurity] Init actuator userDetailsService. username={}", props.username());
        UserDetails actuatorUser = User.withUsername(props.username())
            .password(encoder.encode(props.password()))
            .roles("ACTUATOR")
            .build();

        return new MapReactiveUserDetailsService(actuatorUser);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(
        MapReactiveUserDetailsService actuatorReactiveUserDetailsService,
        PasswordEncoder encoder
    ) {
        log.info("[ColombusSecurity] Init ReactiveAuthenticationManager for actuator. udsClass={}, encoderClass={}",
            actuatorReactiveUserDetailsService.getClass().getSimpleName(),
            encoder.getClass().getSimpleName());
        UserDetailsRepositoryReactiveAuthenticationManager m =
            new UserDetailsRepositoryReactiveAuthenticationManager(actuatorReactiveUserDetailsService);
        m.setPasswordEncoder(encoder);
        return m;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "security.jwt",
        name = "jwk-set-uri"
    )
    public ReactiveJwtDecoder colombusReactiveJwtDecoder(JwtSecurityProperties props) {
        log.info("[ColombusSecurity] Init ReactiveJwtDecoder. issuer={}, jwkSetUri={}, requiredAud={}",
            props.issuer(), props.jwkSetUri(), props.requiredAudience());
        return ColombusJwtSupport.reactiveJwtDecoder(props);
    }

    @Bean
    @ConditionalOnMissingBean(name = "colombusReactiveJwtAuthConverter")
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> colombusReactiveJwtAuthConverter() {
        // log.info("[ColombusSecurity] Init colombusReactiveJwtAuthConverter (reactive).");
        // return ColombusJwtSupport.reactiveJwtAuthConverter();
        Converter<Jwt, Mono<AbstractAuthenticationToken>> delegate =
            ColombusJwtSupport.reactiveJwtAuthConverter();

        return jwt -> {
            String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "null";
            String sub = jwt.getSubject();
            var aud = jwt.getAudience();
            String scope = jwt.getClaimAsString("scope");

            log.info("[ColombusSecurity] Convert JWT: iss={}, sub={}, aud={}, scope={}",
                iss, sub, aud, scope
            );

            return delegate.convert(jwt);
        };
    }

    // /actuator/** 체인
    @Bean
    @Order(0)
    @ConditionalOnBean(ReactiveAuthenticationManager.class)
    @ConditionalOnProperty(
        prefix = "security.actuator",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public SecurityWebFilterChain actuatorSecurityFilterChain(
        ServerHttpSecurity http,
        ReactiveAuthenticationManager authenticationManager,
        ActuatorSecurityProperties props
    ) {
        log.info("[ColombusSecurity] Building ACTUATOR security chain. path=/actuator/**, username={}", props.username());
        http.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/actuator/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authenticationManager(authenticationManager)
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable())
            .authorizeExchange(ex -> ex
                .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyExchange().hasRole("ACTUATOR")
            )
            .exceptionHandling(e -> e.authenticationEntryPoint(
                new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    // /internal/** local
    @Bean
    @Order(1)
    @Profile("local")
    @ConditionalOnProperty(
        prefix = "security.internal",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public SecurityWebFilterChain internalPermitAllChain(ServerHttpSecurity http) {
        log.info("[ColombusSecurity] Building INTERNAL permitAll chain (profile=local). path=/internal/**");
        http.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/internal/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex.anyExchange().permitAll());
        return http.build();
    }

    // /internal/** !local
    @Bean
    @Order(1)
    @Profile("!local")
    @ConditionalOnProperty(
        prefix = "security.internal",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnBean(ReactiveJwtDecoder.class)
    public SecurityWebFilterChain internalSecureChain(
        ServerHttpSecurity http,
        ReactiveJwtDecoder jwtDecoder,
        Converter<Jwt, Mono<AbstractAuthenticationToken>> colombusReactiveJwtAuthConverter
    ) {
        log.info("[ColombusSecurity] Building INTERNAL secure chain (profile!=local). path=/internal/**, jwtDecoder={}, authConverter={}",
            jwtDecoder.getClass().getSimpleName(),
            colombusReactiveJwtAuthConverter.getClass().getSimpleName());
        http.securityMatcher(new PathPatternParserServerWebExchangeMatcher("/internal/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex.anyExchange().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(j -> j.jwtDecoder(jwtDecoder)
                    .jwtAuthenticationConverter(colombusReactiveJwtAuthConverter)));
        return http.build();
    }
}
