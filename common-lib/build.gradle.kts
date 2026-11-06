// Inherit from root
plugins {
    `java-library`
}
dependencies {
    api("org.springframework.security:spring-security-oauth2-jose")
    api("org.springframework:spring-web")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
tasks.test { useJUnitPlatform() }
