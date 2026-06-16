pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        SERVER_HOST = 'taehyung@host.docker.internal'
        SERVER_BASE = '/home/taehyung/apps/msa-server'
        SERVICE_DIR = 'inventory-service'
        COMPOSE_SERVICE = 'inventory-service'
        HEALTH_URL = 'https://api.erp007.xyz/api/inventory/health'
    }

    stages {
        stage('Test') {
            steps {
                sh './gradlew test --no-daemon'
            }
        }

        stage('Deploy') {
            steps {
                sshagent(credentials: ['erp007-server-ssh']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${SERVER_HOST} '
                            set -eu
                            cd ${SERVER_BASE}/${SERVICE_DIR}
                            git pull --ff-only origin main

                            cd ${SERVER_BASE}/infra
                            git pull --ff-only origin main
                            ./scripts/init-server-secrets.sh
                            docker compose -f docker-compose.yml -p msa-server config >/tmp/msa-server-compose.yml
                            docker compose -f docker-compose.yml -p msa-server up -d --build --no-deps ${COMPOSE_SERVICE}
                        '
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    curl -fsS --retry 10 --retry-all-errors --retry-delay 3 --connect-timeout 5 --max-time 10 "$HEALTH_URL" >/dev/null
                '''
            }
        }
    }
}
