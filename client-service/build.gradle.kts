dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.webjars:webjars-locator-core")
    implementation("org.webjars:sockjs-client")
    implementation("org.webjars:stomp-websocket")
    implementation("org.webjars:bootstrap")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}


