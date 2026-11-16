import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.zwobble.mammoth:mammoth:1.13.0")
    implementation("org.apache.commons:commons-secure-xml:1.1.0")
    implementation("org.apache.poi:poi-scratchpad:5.5.1")
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
    implementation("com.fasterxml:aalto-xml:1.3.3")
    implementation("stax:stax-api:1.0.1")
}

tasks.named<ShadowJar>("shadowJar") {
    // The local SimpleSax comes first and supplies Android's secure XML configuration.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/versions/**", "module-info.class", "META-INF/*.SF", "META-INF/*.RSA")
    exclude("META-INF/services/javax.xml.stream.*")
    // Match poi-on-android's separation from Android's boot class path and desktop APIs.
    relocate("javax.xml.namespace", "org.apache.poi.javax.xml.namespace")
    relocate("javax.xml.stream", "org.apache.poi.javax.xml.stream")
    relocate("java.awt", "org.apache.poi.java.awt")
    relocate("javax.imageio", "org.apache.poi.javax.imageio")
    relocate("org.apache.commons.io", "com.huangder.word.internal.commons.io")
    relocate("org.apache.commons.codec", "com.huangder.word.internal.commons.codec")
}
