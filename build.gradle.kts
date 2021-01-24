plugins {
  application
  java
}

group = "org.iadb.tech.bid.quantum"
version = "1.0-SNAPSHOT"
val vertxVersion by extra("4.0.0")
val tuweniVersion by extra("1.3.0")
val mainVerticleName by extra ("org.iadb.tech.quantum.MainVerticle")

repositories {
  jcenter()
  maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/lacchain/liboqs-java/")
    credentials {
      username = System.getenv("USERNAME")
      password = System.getenv("TOKEN")
    }
  }
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
  implementation("io.vertx:vertx-web-client:$vertxVersion")
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-core:$vertxVersion")
  implementation("org.apache.tuweni:bytes:$tuweniVersion")
  implementation("org.apache.tuweni:crypto:$tuweniVersion")
  implementation("org.apache.tuweni:eth:$tuweniVersion")
  implementation("org.apache.tuweni:rlp:$tuweniVersion")
  implementation("org.apache.tuweni:units:$tuweniVersion")
  implementation("org.bouncycastle:bcprov-jdk15on:1.65.01")
  implementation("org.openquantumsafe:liboqs-java:1.1-SNAPSHOT")
  implementation("org.slf4j:slf4j-api:1.7.25")
  implementation("org.web3j:core:4.8.3")

  runtimeOnly("ch.qos.logback:logback-classic:1.2.3")
  runtimeOnly("com.google.guava:guava:27.0.1-jre")

  testImplementation("io.vertx:vertx-junit5:$vertxVersion")
  testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
  testImplementation("org.hamcrest:hamcrest:2.2")
}

tasks.register<JavaExec>("vertxRun") {
  classpath = sourceSets["main"].runtimeClasspath
  main = "io.vertx.core.Launcher"
  args = listOf("run", mainVerticleName)
}

tasks.test {
  useJUnitPlatform()
}

application {
  applicationName = "pq-relay-signer"
  mainClass.set("io.vertx.core.Launcher")
  applicationDefaultJvmArgs = listOf("-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory")
}

tasks.distZip {
  archiveFileName.set("${archiveBaseName.get()}.${archiveExtension.get()}")
}
