pipeline {
    agent {
        any {
            label 'agent-label'
        }
    }
    stages {
        stage('Build') { 
            steps {
                git url: 'https://github.com/cyrille-leclerc/multi-module-maven-project'

                withMaven(maven: 'M3', mavenSettingsConfig: '49b8c9d8-64d8-4cd1-978e-72f73db7a261', mavenLocalRepo: '/Users/sean/.m2/repository') {
             
                    // Run the maven build
                    sh "mvn clean install -DskipTests=true"
             
                } 
            }
        }
    }
}