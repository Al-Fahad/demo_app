pipeline {
    agent any

    environment {
        EC2_HOST = credentials('EC2_HOST')
        EC2_USER = 'ubuntu'
        APP_DIR = '/var/www/html'
        BACKUP_DIR = '/var/www/backups'
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Al-Fahad/demo_app.git'
            }
        }

        stage('Package') {
            steps {
                sh 'tar -czf app.tar.gz *'
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY'
                    )
                ]) {
                    sh '''
                    echo "Deploying to EC2..."
                    '''
                }
            }
        }
    }

    post {
        failure {
            echo "Deployment failed!"
        }
    }
}
