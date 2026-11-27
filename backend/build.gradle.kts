plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.yangstagram"
version = "0.0.1-SNAPSHOT"
description = "Yangstagram"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	// 배포 빌드(Render)는 공용 IP라 Maven Central이 429(요청 과다)로 막을 때가 있어 Google 미러를 먼저 쓴다
	maven("https://maven-central.storage-download.googleapis.com/maven2/")
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket") // DM·알림 실시간 (STOMP)
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("net.datafaker:datafaker:2.7.0") // 가짜 데이터 생성
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
	maxHeapSize = "512m"
}

// 같은 PC에서 다른 프로젝트(yangpang 배포 서버)도 돌아가므로 메모리를 작게 잡는다
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("-Xms128m", "-Xmx384m", "-XX:+UseSerialGC")
}

// ------------------------------------------------------------ 도구: 가짜 릴스 영상 만들기
// 앱 실행에는 필요 없고, seed-media/reels/ 를 한 번 만들 때만 쓴다 (gradlew reelClips)
val tools: SourceSet = sourceSets.create("tools")
dependencies {
	"toolsImplementation"("org.jcodec:jcodec-javase:0.2.5")
}
tasks.register<JavaExec>("reelClips") {
	group = "yangstagram"
	description = "시드 사진으로 가짜 릴스 영상(MP4)을 만든다"
	classpath = tools.runtimeClasspath
	mainClass = "com.yangstagram.tools.ReelClipGenerator"
	maxHeapSize = "256m"
	workingDir = projectDir
}
