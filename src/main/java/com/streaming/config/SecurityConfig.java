package com.streaming.config;

import com.streaming.security.JwtRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // Para APIs REST
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // JWT es stateless!
            .and()
            .authorizeRequests()
            // Endpoints públicos
            .antMatchers("/api/auth/**").permitAll()
            .antMatchers(HttpMethod.GET, "/api/contenidos/**").permitAll()
            .antMatchers(HttpMethod.GET, "/api/ranking/**").permitAll()
            // Contenido estático
            .antMatchers("/", "/index.html", "/js/**", "/css/**").permitAll()
            
            // CRUD de Contenidos: SÓLO ADMIN! (nota: hasAuthority mapea directamente al string ROLE_ADMIN)
            .antMatchers(HttpMethod.POST, "/api/contenidos/**").hasAuthority("ROLE_ADMIN")
            .antMatchers(HttpMethod.PUT, "/api/contenidos/**").hasAuthority("ROLE_ADMIN")
            .antMatchers(HttpMethod.DELETE, "/api/contenidos/**").hasAuthority("ROLE_ADMIN")
            
            // Cualquier otra petición debe estar autenticada
            .anyRequest().authenticated();
            
        // Registrar nuestro filtro personalizado ANTES del filtro estandar de Username/Password
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
