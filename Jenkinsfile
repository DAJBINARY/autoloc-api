// CI/CD pipeline of AutoLoc API.
// Runs on the Jenkins of ci/docker-compose.yml: Docker is reached through the host's socket and the
// disposable MySQL container joins the "autoloc-ci" network shared with Jenkins and SonarQube.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers {
        // Jenkins runs on localhost, so GitHub cannot call it: it checks the repository every 5 minutes
        pollSCM('H/5 * * * *')
    }

    environment {
        CI_NETWORK   = 'autoloc-ci'
        DB_CONTAINER = "autoloc-mysql-${env.BUILD_NUMBER}"
        IMAGE        = "${env.DOCKERHUB_USER}/autoloc-api"
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
                script {
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.TAG = "${env.BUILD_NUMBER}-${env.GIT_SHORT}"
                }
                echo "Commit ${env.GIT_SHORT}, image ${env.IMAGE}:${env.TAG}"
            }
        }

        stage('Base de données') {
            environment {
                DB_PASSWORD = credentials('ci-db-password')
            }
            steps {
                sh '''
                    docker run -d --name "$DB_CONTAINER" --network "$CI_NETWORK" \
                        -e MYSQL_ROOT_PASSWORD="$DB_PASSWORD" -e MYSQL_DATABASE=autoloc_db \
                        --health-cmd 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
                        --health-interval 3s --health-retries 40 \
                        mysql:8.4
                    for i in $(seq 1 60); do
                        [ "$(docker inspect -f '{{.State.Health.Status}}' "$DB_CONTAINER")" = healthy ] && break
                        sleep 3
                    done
                    docker inspect -f 'MySQL {{.State.Health.Status}}' "$DB_CONTAINER"
                '''
                // The application starts against MySQL: connection, schema created by Hibernate
                sh '''
                    AUTOLOC_DB_URL="jdbc:mysql://$DB_CONTAINER:3306/autoloc_db" \
                    AUTOLOC_DB_PASSWORD="$DB_PASSWORD" \
                    ./mvnw -B -ntp -Dtest=MySqlConnexionTest -Dsurefire.failIfNoSpecifiedTests=false test
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/TEST-*MySqlConnexionTest.xml'
                    sh 'docker rm -f "$DB_CONTAINER" || true'
                }
            }
        }

        stage('Tests') {
            steps {
                // Unit, integration and functional tests, JaCoCo coverage, then the jar
                sh './mvnw -B -ntp verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/TEST-*.xml'
                }
                success {
                    archiveArtifacts artifacts: 'target/autoloc-api-*.jar', fingerprint: true
                }
            }
        }

        stage('SonarQube') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh './mvnw -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:sonar'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                // SonarQube calls Jenkins back (webhook) once the analysis is processed
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Build image Docker') {
            steps {
                sh 'docker build -t "$IMAGE:$TAG" -t "$IMAGE:latest" .'
            }
        }

        stage('Push Docker Hub') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub',
                        usernameVariable: 'HUB_USER', passwordVariable: 'HUB_TOKEN')]) {
                    sh '''
                        echo "$HUB_TOKEN" | docker login -u "$HUB_USER" --password-stdin
                        docker push "$IMAGE:$TAG"
                        docker push "$IMAGE:latest"
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true'
            sh 'docker logout >/dev/null 2>&1 || true'
        }
        success {
            echo "Image publiée : ${env.IMAGE}:${env.TAG}"
        }
    }
}
