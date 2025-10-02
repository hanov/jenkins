#!groovy

import jenkins.model.*
import hudson.model.*
import hudson.security.*

def instance = Jenkins.getInstance()

// Enable Jenkins API for all authenticated users
def authStrategy = instance.getAuthorizationStrategy()
if (authStrategy instanceof FullControlOnceLoggedInAuthorizationStrategy) {
    // The current strategy already allows API access for authenticated users
    println "API access already enabled for authenticated users"
} else {
    // Set up matrix-based security if needed
    println "Setting up API access permissions"
}

// Enable CSRF protection bypass for API calls (if needed)
def crumbIssuer = instance.getCrumbIssuer()
if (crumbIssuer == null) {
    // CSRF protection is already disabled, which is good for API access
    println "CSRF protection disabled - API calls will work"
} else {
    println "CSRF protection enabled - API calls may need crumb tokens"
}

// Create a simple test job
def jobName = "test-build-job"
def job = instance.getItem(jobName)

if (!job) {
    println "Creating test job: ${jobName}"
    
    def jobXml = '''<?xml version='1.1' encoding='UTF-8'?>
<project>
  <description>Test job for build cancellation</description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.scm.NullSCM"/>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>true</concurrentBuild>
  <builders>
    <hudson.tasks.Shell>
      <command>#!/bin/bash
echo "Starting long-running test build..."
echo "Build started by: $BUILD_USER_ID"
echo "Build number: $BUILD_NUMBER"
echo "Job name: $JOB_NAME"

# Simulate a long-running build
for i in {1..60}; do
    echo "Step $i of 60 - Processing..."
    sleep 10
done

echo "Build completed successfully!"
</command>
    </hudson.tasks.Shell>
  </builders>
  <publishers/>
  <buildWrappers>
    <org.jenkinsci.plugins.builduser.BuildUser/>
  </buildWrappers>
</project>'''

    try {
        def project = instance.createProjectFromXML(jobName, new ByteArrayInputStream(jobXml.getBytes()))
        println "Created test job: ${jobName}"
    } catch (Exception e) {
        println "Failed to create test job: ${e.message}"
        
        // Create a simple freestyle project programmatically
        def project = new hudson.model.FreeStyleProject(instance, jobName)
        project.setDescription("Test job for build cancellation")
        
        // Add a shell build step
        def shellStep = new hudson.tasks.Shell('''#!/bin/bash
echo "Starting long-running test build..."
echo "Build started by: \\$USER"
echo "Build number: \\$BUILD_NUMBER"
echo "Job name: \\$JOB_NAME"

# Simulate a long-running build
for i in {1..60}; do
    echo "Step \\$i of 60 - Processing..."
    sleep 10
done

echo "Build completed successfully!"
''')
        
        project.getBuildersList().add(shellStep)
        instance.putItem(project)
        println "Created simple test job: ${jobName}"
    }
}

instance.save()
println "Jenkins API setup completed"