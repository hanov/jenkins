#!groovy

import jenkins.model.*
import hudson.security.*
import org.kohsuke.stapler.StaplerRequest
import net.sf.json.JSONObject

def instance = Jenkins.getInstance()

println "Setting up SAML authentication for Jenkins with Cognito..."

// Note: This script sets up the foundation for SAML
// You'll need to install the SAML plugin first and configure it through the UI
// This script prepares the security realm settings

try {
    // Enable matrix-based security for now
    def strategy = new GlobalMatrixAuthorizationStrategy()
    
    // Grant admin permissions to admin user and SAML users
    strategy.add(Jenkins.ADMINISTER, "admin")
    strategy.add(Jenkins.READ, "authenticated")
    strategy.add(Item.BUILD, "authenticated")
    strategy.add(Item.CANCEL, "authenticated")
    strategy.add(Item.READ, "authenticated")
    
    instance.setAuthorizationStrategy(strategy)
    
    println "Matrix-based security configured for SAML integration"
    
    // Save configuration
    instance.save()
    
    println """
SAML Setup Instructions:
1. Install SAML Plugin in Jenkins:
   - Go to Manage Jenkins > Manage Plugins
   - Search for 'SAML' and install the SAML plugin
   
2. Configure Cognito as SAML Identity Provider:
   - In AWS Cognito User Pool, go to App Integration > App client settings
   - Enable SAML identity provider
   - Set callback URL to: http://localhost:8080/securityRealm/finishLogin
   
3. Configure Jenkins SAML:
   - Go to Manage Jenkins > Configure Global Security
   - Select 'SAML 2.0' as Security Realm
   - Use the Cognito SAML metadata URL
   
4. Alternative: Use OpenID Connect plugin for easier setup
"""

} catch (Exception e) {
    println "Error setting up SAML foundation: ${e.message}"
    e.printStackTrace()
}