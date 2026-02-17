pipeline {
    agent any

    environment {
        EC2_HOST = credentials('EC2_HOST')
        EC2_USER = credentials('EC2_USER')
        SSH_KEY_ID = 'ec2-ssh-key'   // SSH Username with private key credential ID
        APP_DIR = '/var/www/html'
        BACKUP_DIR = '/var/www/backups'
        TIMESTAMP = "${new Date().format('yyyyMMddHHmmss')}"
    }

    triggers {
        githubPush()
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
                sh '''
                tar -czf app.tar.gz *
                '''
            }
        }

        stage('Deploy to EC2') {
            steps {
                sshagent(credentials: [SSH_KEY_ID]) {
                    sh """
                    ssh -o StrictHostKeyChecking=no $EC2_USER@$EC2_HOST << EOF
                    set -e

                    sudo mkdir -p $BACKUP_DIR

                    # Backup current version
                    if [ -d "$APP_DIR" ]; then
                      sudo tar -czf $BACKUP_DIR/backup-$TIMESTAMP.tar.gz -C $APP_DIR .
                    fi

                    # Prepare new deployment folder
                    sudo rm -rf $APP_DIR/*
                    sudo mkdir -p $APP_DIR
                    EOF
                    """

                    sh """
                    scp -o StrictHostKeyChecking=no app.tar.gz $EC2_USER@$EC2_HOST:/tmp/
                    """

                    sh """
                    ssh -o StrictHostKeyChecking=no $EC2_USER@$EC2_HOST << EOF
                    set -e

                    sudo tar -xzf /tmp/app.tar.gz -C $APP_DIR
                    sudo chown -R www-data:www-data $APP_DIR

                    # Install nginx if not installed
                    if ! command -v nginx >/dev/null 2>&1; then
                        sudo apt update -y
                        sudo apt install -y nginx
                    fi

                    sudo systemctl enable nginx
                    sudo systemctl restart nginx
                    EOF
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                sshagent(credentials: [SSH_KEY_ID]) {
                    sh """
                    ssh -o StrictHostKeyChecking=no $EC2_USER@$EC2_HOST '
                    if ! systemctl is-active --quiet nginx; then
                        echo "Nginx is not running!"
                        exit 1
                    fi
                    '
                    """
                }
            }
        }
    }

    post {
        failure {
            echo "Deployment failed! Rolling back..."

            sshagent(credentials: [SSH_KEY_ID]) {
                sh """
                ssh -o StrictHostKeyChecking=no $EC2_USER@$EC2_HOST << EOF
                set -e

                LAST_BACKUP=\$(ls -t $BACKUP_DIR | head -n 1)

                if [ -n "\$LAST_BACKUP" ]; then
                    sudo rm -rf $APP_DIR/*
                    sudo tar -xzf $BACKUP_DIR/\$LAST_BACKUP -C $APP_DIR
                    sudo chown -R www-data:www-data $APP_DIR
                    sudo systemctl restart nginx
                    echo "Rollback completed."
                else
                    echo "No backup found!"
                fi
                EOF
                """
            }
        }

        success {
            echo "Deployment successful!"
        }
    }
}
