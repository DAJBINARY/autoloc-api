# Jenkins controller of the AutoLoc CI: plugins, configuration and job are all defined as code.
FROM jenkins/jenkins:lts-jdk21

# Docker CLI and buildx: the pipeline builds and pushes images through the host's Docker daemon
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker/buildx-bin /buildx /usr/libexec/docker/cli-plugins/docker-buildx

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY casc.yaml /var/jenkins_home/casc_configs/casc.yaml
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc_configs/casc.yaml \
    JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
