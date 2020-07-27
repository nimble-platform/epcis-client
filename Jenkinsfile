#!/usr/bin/env groovy

node('nimble-jenkins-slave') {

    stage('Clone and Update') {
        git(url: 'https://github.com/nimble-platform/epcis-client.git', branch: env.BRANCH_NAME)
    }

    stage('Build Java') {
        sh 'mvn clean package -DskipTests'
    }

    if (env.BRANCH_NAME == 'staging') {
        stage('Build Docker') {
            sh 'mvn docker:build -P docker -DdockerImageTag=staging'
        }

        stage('Push Docker') {
            sh 'docker push nimbleplatform/epcis-client:staging'
        }
    }
}
