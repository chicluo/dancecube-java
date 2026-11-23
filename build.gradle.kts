plugins {
    java
    `java-library`
}

group = "com.chicluo"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // 以下依赖暴露给 consumer 运行期（AudioProbe/导出等 public API 会用），必须用 api 而非 implementation，
    // 否则 java-library 的 implementation 不传递，调用方运行时 NoClassDefFoundError。
    api("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.4")
    // 简繁转换（对标 Python 版的 zhconv）
    implementation("com.github.houbb:opencc4j:1.8.1")
    // 5.4.0 修复 CVE-2025-31672（OOXML zip 重复条目）
    api("org.apache.poi:poi-ooxml:5.4.0")
    // 音频时长探测（对标 Python 的 tinytag）；Central 上只有 2.0.1，Sonatype 确认无已知漏洞
    api("org.jaudiotagger:jaudiotagger:2.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 25
}
