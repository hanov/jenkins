# Stage 0: get official debian keyring
FROM debian:bookworm-slim AS keyring
# Install ca-certificates first to enable HTTPS downloads
RUN apt-get update --allow-insecure-repositories \
 && apt-get install -y --no-install-recommends --allow-unauthenticated ca-certificates wget gnupg \
 && wget -qO- https://ftp-master.debian.org/keys/archive-key-12.asc | gpg --dearmor > /usr/share/keyrings/debian-archive-keyring.gpg \
 && apt-get update \
 && apt-get install -y --no-install-recommends debian-archive-keyring \
 && rm -rf /var/lib/apt/lists/*

FROM jenkins/jenkins:lts

# Switch to root to install plugins
USER root

# Copy keyring from previous stage
COPY --from=keyring /usr/share/keyrings/debian-archive-keyring.gpg /usr/share/keyrings/

# Install docker CLI (optional, for docker-in-docker if needed)
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

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