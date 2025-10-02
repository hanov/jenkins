#!groovy

import jenkins.model.*
import hudson.security.*
import org.kohsuke.stapler.StaplerRequest
import net.sf.json.JSONObject

def instance = Jenkins.getInstance()

// Get environment variables
def cognitoDomain = System.getenv('COGNITO_DOMAIN') ?: 'jenkins-auth-62745.auth.us-east-1.amazoncognito.com'
def cognitoUserPoolId = System.getenv('COGNITO_USER_POOL_ID') ?: 'us-east-1_mHHkRBGwp'
def cognitoClientId = System.getenv('COGNITO_CLIENT_ID') ?: '8suo93gn4lp3vm0dhv3prjtdn'
def cognitoClientSecret = System.getenv('COGNITO_CLIENT_SECRET') ?: ''

println "Configuring Jenkins OpenID Connect authentication with Cognito..."
println "Domain: ${cognitoDomain}"
println "User Pool ID: ${cognitoUserPoolId}"
println "Client ID: ${cognitoClientId}"

try {
    // Check if OIC plugin is available
    def oicPlugin = instance.getPlugin('oic-auth')
    if (!oicPlugin) {
        println "OpenID Connect Authentication plugin not found. Please install it first."
        return
    }
    
    println "OpenID Connect plugin found, configuring..."
    
    // Configure OpenID Connect Security Realm
    def securityRealm = new org.jenkinsci.plugins.oic.OicSecurityRealm(
        cognitoClientId,                    // clientId
        cognitoClientSecret,                // clientSecret
        null,                               // wellKnownOpenIDConfigurationUrl (will be set below)
        "openid email profile",             // scopes
        false,                              // disableSslVerification
        false,                              // logoutFromOpenidProvider
        "preferred_username",               // userNameField
        "name",                            // fullNameFieldName
        "email",                           // emailFieldName
        null,                              // groupsFieldName
        null,                              // escapeHatchEnabled
        null,                              // escapeHatchUsername
        null,                              // escapeHatchSecret
        null,                              // escapeHatchGroup
        null                               // automanualconfigure
    )
    
    // Set the well-known configuration URL
    securityRealm.setWellKnownOpenIDConfigurationUrl("https://cognito-idp.us-east-1.amazonaws.com/${cognitoUserPoolId}/.well-known/openid-configuration")
    
    // Apply the security realm
    instance.setSecurityRealm(securityRealm)
    
    // Set up authorization strategy
    def strategy = new GlobalMatrixAuthorizationStrategy()
    
    // Grant permissions to authenticated users
    strategy.add(Jenkins.READ, "authenticated")
    strategy.add(Item.BUILD, "authenticated")
    strategy.add(Item.CANCEL, "authenticated")
    strategy.add(Item.READ, "authenticated")
    strategy.add(Item.WORKSPACE, "authenticated")
    
    // Grant admin permissions to admin user (fallback)
    strategy.add(Jenkins.ADMINISTER, "admin")
    
    // Grant admin permissions to specific Cognito users if needed
    // strategy.add(Jenkins.ADMINISTER, "your-cognito-username")
    
    instance.setAuthorizationStrategy(strategy)
    
    // Save configuration
    instance.save()
    
    println "OpenID Connect authentication configured successfully!"
    println "Logout of Jenkins and you should see 'Login with Openid Connect' option"
    
} catch (Exception e) {
    println "Error configuring OpenID Connect: ${e.message}"
    e.printStackTrace()
    
    // Fallback to basic security for now
    println "Setting up fallback security configuration..."
    
    def strategy = new GlobalMatrixAuthorizationStrategy()
    strategy.add(Jenkins.ADMINISTER, "admin")
    strategy.add(Jenkins.READ, "authenticated")
    strategy.add(Item.BUILD, "authenticated")
    strategy.add(Item.CANCEL, "authenticated")
    strategy.add(Item.READ, "authenticated")
    
    instance.setAuthorizationStrategy(strategy)
    instance.save()
}