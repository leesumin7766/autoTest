import java.util.zip.ZipFile

// Inherit from root
dependencies {
    implementation(project(":common-lib"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("software.amazon.awssdk:s3:2.25.65")
    implementation("software.amazon.awssdk:aws-core:2.25.65")
    implementation("org.flywaydb:flyway-core")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Document text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-scratchpad:5.3.0")
    implementation("kr.dogfoot:hwplib:1.1.8")
    implementation("kr.dogfoot:hwpxlib:1.0.9")

    testImplementation("org.mockito:mockito-core:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from(rootProject.file("frontend/public/downloads/menu-tree-common-rules.md")) { into("menu-templates") }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    doLast {
        val required = listOf(
            "com/autotest/test_management_service/presentation/controller/MenuTcController.class",
            "com/autotest/test_management_service/application/testcase/MenuTcService.class",
            "com/autotest/test_management_service/application/testcase/MenuTcGenerator.class",
            "com/autotest/test_management_service/application/testcase/UploadedMenuService.class",
            "com/autotest/test_management_service/presentation/controller/TestExecutionController.class",
            "com/autotest/test_management_service/presentation/controller/TestExecutionInternalController.class",
            "com/autotest/test_management_service/application/testcase/ExecutionReadinessService.class",
            "com/autotest/test_management_service/application/testcase/TestExecutionWorker.class",
            "com/autotest/test_management_service/application/testcase/ExecutionReviewService.class",
            "tc-templates/menu-tc-v1.json"
        )
        ZipFile(archiveFile.get().asFile).use { jar ->
            check(required.all { jar.getEntry("BOOT-INF/classes/$it") != null }) {
                "TC 업로드·생성 API 또는 템플릿이 실행 JAR에서 누락되었습니다."
            }
        }
    }
}

tasks.register<JavaExec>("generateMenuTcSamples") {
    group = "verification"
    description = "Generate independent TC workbooks and execution JSON from user menu samples"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.autotest.test_management_service.application.testcase.MenuTcCli")
    args(rootProject.file("sample/menu sample/menu sample.xlsx").absolutePath,
        rootProject.file("output/menu-tc-samples").absolutePath)
}
