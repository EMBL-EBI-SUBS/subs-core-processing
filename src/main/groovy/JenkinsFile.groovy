node {
    stage('Preparation') { // for display purposes
        checkout scm
        gradleHome = tool 'gradle-3'
    }
    stage('Build') {
        // Run the gradle assemble
        sh "gradlew assemble"
    }
    stage('Deploy') {
        if (env.BRANCH_NAME == 'dev') {
            sh "gradlew -Penv=dev deployJar"
        } else if (env.BRANCH_NAME == 'master') {
            sh "gradlew -Penv=test deployJar"
        }
    }
}