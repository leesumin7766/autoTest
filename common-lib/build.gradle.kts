// Inherit from root
plugins {
    `java-library`
}
dependencies {
    api("org.springframework:spring-web")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
tasks.test { useJUnitPlatform() }
