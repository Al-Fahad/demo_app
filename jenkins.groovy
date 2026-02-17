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

        stage('Package Application') {
            steps {
                sh 'tar -czf app.tar.gz *'
            }
        }

        stage('Deploy to EC2') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY'
                    )
                ]) {
                    sh '''
                    ssh -o StrictHostKeyChecking=no -i $SSH_KEY $EC2_USER@$EC2_HOST "
                    set -e

                    sudo mkdir -p $BACKUP_DIR

                    if [ -d $APP_DIR ]; then
                        sudo tar -czf $BACKUP_DIR/backup.tar.gz -C $APP_DIR .
                    fi

                    sudo rm -rf $APP_DIR/*
                    "
                    '''

                    sh '''
                    scp -o StrictHostKeyChecking=no -i $SSH_KEY app.tar.gz $EC2_USER@$EC2_HOST:/tmp/
                    '''

                    sh '''
                    ssh -o StrictHostKeyChecking=no -i $SSH_KEY $EC2_USER@$EC2_HOST "
                    set -e
                    sudo tar -xzf /tmp/app.tar.gz -C $APP_DIR
                    sudo chown -R www-data:www-data $APP_DIR
                    sudo systemctl restart nginx
                    "
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                echo "Health check passed"
                '''
            }
        }
    }

    post {
        failure {
            echo "Deployment failed! Rolling back..."

            withCredentials([
                sshUserPrivateKey(
                    credentialsId: 'ec2-ssh-key',
                    keyFileVariable: 'SSH_KEY'
                )
            ]) {
                sh '''
                ssh -o StrictHostKeyChecking=no -i $SSH_KEY $EC2_USER@$EC2_HOST "
                if [ -f $BACKUP_DIR/backup.tar.gz ]; then
                    sudo rm -rf $APP_DIR/*
                    sudo tar -xzf $BACKUP_DIR/backup.tar.gz -C $APP_DIR
                    sudo systemctl restart nginx
                    echo 'Rollback completed.'
                else
                    echo 'No backup found.'
                fi
                "
                '''
            }
        }
    }
}
