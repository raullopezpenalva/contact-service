
pipeline {
    agent { label 'maven'}
    
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                credentialsId: 'Jenkins-lab',
                url: 'https://github.com/raullopezpenalva/contact-service.git'
            }
        }
        stage('Compile') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }
        stage('Unit Tests') {
            steps {
                sh 'mvn test'
            }
        }
    }
}