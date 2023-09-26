#!/usr/bin/env groovy
import com.hiraethci.deployer.constant.ProjectType

def call(String params, ProjectType projectType, ctx) {
    if (ctx) {
        stage("Code Review") {
            withSonarQubeEnv(params) {
                if (projectType == ProjectType.Maven) {
                    ctx.sh 'mvn clean package -am -D"maven.test.skip=true" org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar'
                } else if (projectType == ProjectType.Gradle) {
                    ctx.sh 'chmod +x gradlew || true'
                    ctx.sh './gradlew clean assemble -x test sonarqube'
                } else {
                    // def scannerHome = tool 'sonar-scanner';
                    ctx.sh "${tool("sonar-scanner")}/bin/sonar-scanner"
                }
            }
        }
    }
}