pipeline {
    agent {
        any {
            label 'agent-label'
        }
    }
    stages {
        stage('Build') { 
            steps {
                git url: 'https://github.com/sdumont2/dropbox-connector'
                withMaven(maven: 'M3', mavenSettingsConfig: '49b8c9d8-64d8-4cd1-978e-72f73db7a261', mavenLocalRepo: '.repository') {
             
                    // Run the maven build
                    sh "mvn clean install -Dmaven.test.skip=true"
             
                } 
            }
        }
    }
}