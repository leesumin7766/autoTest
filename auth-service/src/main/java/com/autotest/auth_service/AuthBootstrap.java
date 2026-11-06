package com.autotest.auth_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class AuthBootstrap implements ApplicationRunner {
    private final AuthService service;
    private final String username,password,displayName;
    AuthBootstrap(AuthService service,@Value("${AUTH_BOOTSTRAP_ADMIN_USERNAME:}") String username,
        @Value("${AUTH_BOOTSTRAP_ADMIN_PASSWORD:}") String password,@Value("${AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME:Administrator}") String displayName) {
        this.service=service;this.username=username;this.password=password;this.displayName=displayName;
    }
    public void run(ApplicationArguments args) { service.bootstrap(username,password,displayName); }
}
