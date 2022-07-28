plugins {
    scala
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.13.8")
}

// tag::zinc-dependency[]
scala {
    zincVersion.set("1.7.1")
}
// end::zinc-dependency[]
