node {
    def mvnHome
    stage('Preparation') { // for display purposes
        // Get some code from a GitHub repository
        echo 'Pulling...' + env.BRANCH_NAME
        checkout scm
        gradleHome = tool 'gradle-3'
    }
    stage('Build') {
        // Run the gradle assemble
        sh "gradlew assemble"
    }
    stage('Build') {
        // Run the gradle assemble
        sh "gradlew assemble"
    }
    stage('Deploy') {
        echo env.BRANCH_NAME
    }
}