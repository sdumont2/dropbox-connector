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

                withMaven(maven: 'M3', mavenSettingsConfig: 'my-maven-settings', mavenLocalRepo: '.repository') {
             
                    // Run the maven build
                    bat "mvn clean install -DskipTests=true"
             
                } 
            }
        }
    }
}