// Inherit from root
dependencies {
    implementation(project(":common-lib"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}