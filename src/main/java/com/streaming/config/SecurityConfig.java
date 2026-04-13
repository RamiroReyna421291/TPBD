package com.streaming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de Seguridad.
 * 
 * En C#, esto sería tu configuración en 'Program.cs' usando Identity o JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Bean para hashear contraseñas usando BCrypt.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configura qué rutas son públicas y cuáles requieren autenticación.
     * Por ahora, dejaremos todo abierto para que puedas seguir probando 
     * fácilmente, pero habilitamos el filtro de seguridad.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // Desactivamos CSRF (para APIs es común)
            .authorizeRequests()
            .antMatchers("/**").permitAll() // Permitimos todo por ahora para testeo
            .anyRequest().authenticated();
        
        return http.build();
    }
}
