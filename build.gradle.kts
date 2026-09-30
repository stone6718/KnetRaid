plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    // Vault 본체가 설치되어 있을 때만 런타임에 실제 구현체가 연결되는 소프트 의존성.
    // Vault 미설치 서버에서도 정상 동작해야 하므로 shadow에 포함하지 않고 compileOnly로만 사용한다.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    // Floodgate(Bedrock 연동)도 동일한 이유로 compileOnly 소프트 의존성으로만 사용한다.
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")

    // 데이터베이스 연결 풀 및 JDBC 드라이버 (플러그인 JAR에 shadow로 내장)
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.20.1")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dstdin.encoding=UTF-8")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    shadowJar {
        archiveClassifier.set("")
        // HikariCP만 재배치한다. sqlite-jdbc/mariadb-java-client는 네이티브 라이브러리 및
        // JDBC 드라이버 자동 등록 리소스 경로가 패키지명에 하드코딩되어 있어 재배치 시
        // 깨질 위험이 있으므로 원본 패키지를 그대로 사용한다.
        relocate("com.zaxxer.hikari", "kr.knetsoft.knetraid.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }
}
