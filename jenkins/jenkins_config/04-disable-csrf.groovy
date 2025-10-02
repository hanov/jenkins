#!groovy

import jenkins.model.*
import hudson.security.*

def instance = Jenkins.getInstance()

// Disable CSRF protection for easier API testing
instance.setCrumbIssuer(null)

instance.save()
println "CSRF protection disabled for API testing"