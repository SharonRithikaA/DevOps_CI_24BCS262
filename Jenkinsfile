pipeline {
    agent any

    tools {
        nodejs 'NodeJS'
        maven 'Maven'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                bat 'cd backend && mvn clean package -DskipTests'
                bat 'cd frontend && npm ci && npm run build'
            }
        }

        stage('Test') {
            steps {
                bat 'cd backend && mvn test'
            }
        }

        stage('Result') {
            steps {
                echo 'Build and tests completed successfully.'
            }
        }
    }
}