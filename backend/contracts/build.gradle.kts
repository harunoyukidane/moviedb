import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.protobuf)
    `java-library`
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    api(libs.protobuf.java)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.protobuf.kotlin)
    // Needed so generated gRPC Java compiles on JDK 21 (javax.annotation.Generated).
    compileOnly(libs.annotation.api)
    api(libs.annotation.api)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        id("grpc") {
            artifact = libs.grpc.gen.java.get().toString()
        }
        id("grpckt") {
            artifact = libs.grpc.gen.kotlin.get().toString() + ":jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}
