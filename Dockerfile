# 배포용 이미지 (Render 무료 웹 서비스, 메모리 512MB)
# 화면(React 빌드)과 백엔드(Spring Boot)를 한 서버·한 주소로 내보낸다. DB는 Neon, 사진은 Supabase Storage.

# 1) 화면 빌드
FROM node:22-alpine AS web
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) 백엔드 빌드 (테스트는 노트북에서 돌린다)
FROM eclipse-temurin:25-jdk AS api
WORKDIR /src
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
COPY backend/src ./src
# 라이브러리 저장소가 잠깐 막히면(429) 잠시 뒤 다시 시도
RUN for i in 1 2 3; do sh ./gradlew --no-daemon bootJar -x test && break; echo "다시 시도 $i"; sleep 20; done \
    && cp build/libs/*-SNAPSHOT.jar /app.jar

# 3) 실행
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=api /app.jar /app/app.jar
COPY --from=web /src/dist /app/web
COPY deploy/seed /app/seed
ENV SPRING_PROFILES_ACTIVE=prod
# 512MB 안에서: 힙 280MB, 스레드 스택 작게, 가벼운 GC
ENV JAVA_TOOL_OPTIONS="-Xmx280m -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.awt.headless=true"
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
