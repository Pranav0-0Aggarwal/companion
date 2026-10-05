plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.kotlin.test)
}

sourceSets.test {
    resources.srcDir("../app/src/main/assets")
}

tasks.test {
    useJUnit()
}
