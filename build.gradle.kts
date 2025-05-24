plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.fgbg"
version = "0.0.1"

repositories {
    mavenCentral()

}

dependencies {
    implementation("org.openjfx:javafx-controls:17.0.2")
    implementation("org.openjfx:javafx-fxml:17.0.2")
}

//application {
//    mainclass.set("com.fgbg.lexi.mainkt")
//}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}