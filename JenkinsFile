node {
    def mvnHome
    stage('Preparation') { // for display purposes
        // Get some code from a GitHub repository
        checkout scm
        gradleHome = tool 'gradle-3'
    }
    stage('Build') {
        // Run the gradle assemble
        sh "gradlew assemble"
    }
}