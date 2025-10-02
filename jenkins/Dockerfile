FROM jenkins/jenkins:lts

# Switch to root to install plugins
USER root

# Install system dependencies with explicit architecture support
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    gnupg \
    lsb-release \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Switch back to jenkins user
USER jenkins

# Install required plugins
RUN jenkins-plugin-cli --plugins \
    build-user-vars-plugin \
    matrix-auth \
    oic-auth \
    workflow-aggregator \
    blueocean \
    docker-workflow

# Copy initialization scripts
COPY jenkins_config/*.groovy /usr/share/jenkins/ref/init.groovy.d/

# Skip the initial setup wizard
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"