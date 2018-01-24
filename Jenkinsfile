pipeline {
    agent {
        any {
            label 'agent-label'
        }
    }
    stages {
        stage('Build') { 
            steps {
                sh 'mvn clean install -DskipTests=true' 
            }
        }
    }
}