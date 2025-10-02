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
    // Always try to configure OIDC, even if already configured
    println "Checking current security realm..."
    def currentRealm = instance.getSecurityRealm()
    println "Current realm class: ${currentRealm?.getClass()?.getName()}"
    
    // Check if OIC plugin is available
    def oicPlugin = instance.getPlugin('oic-auth')
    if (!oicPlugin) {
        println "OpenID Connect Authentication plugin not found. Skipping OIDC config."
        return
    }
    
    println "OpenID Connect plugin found, configuring..."
    
    // Always configure OIDC (even if already configured) to ensure persistence
    def securityRealm = new org.jenkinsci.plugins.oic.OicSecurityRealm(
        cognitoClientId,                    // clientId
        cognitoClientSecret,                // clientSecret
        "https://cognito-idp.us-east-1.amazonaws.com/${cognitoUserPoolId}/.well-known/openid-configuration", // wellKnownOpenIDConfigurationUrl
        "openid email profile",             // scopes
        false,                              // disableSslVerification
        false,                              // logoutFromOpenidProvider
        "preferred_username",               // userNameField
        "name",                            // fullNameFieldName
        "email",                           // emailFieldName
        null,                              // groupsFieldName
        false,                             // escapeHatchEnabled
        null,                              // escapeHatchUsername
        null,                              // escapeHatchSecret
        null,                              // escapeHatchGroup
        "auto"                             // automanualconfigure
    )
    
    // Apply the security realm
    instance.setSecurityRealm(securityRealm)
    println "✅ OIDC Security Realm configured"
    
    // Set up authorization strategy
    def strategy = new GlobalMatrixAuthorizationStrategy()
    
    // Grant permissions to authenticated users
    strategy.add(Jenkins.READ, "authenticated")
    strategy.add(Item.BUILD, "authenticated")
    strategy.add(Item.CANCEL, "authenticated")
    strategy.add(Item.READ, "authenticated")
    strategy.add(Item.WORKSPACE, "authenticated")
    strategy.add(Item.CONFIGURE, "authenticated")
    
    // Grant admin permissions to admin user (fallback)
    strategy.add(Jenkins.ADMINISTER, "admin")
    
    // Grant admin permissions to specific Cognito users
    strategy.add(Jenkins.ADMINISTER, "user-jenkins")  // Your Cognito username
    strategy.add(Jenkins.ADMINISTER, "64d81418-a071-70bf-5d73-b2df0b046569")  // Your Jenkins user ID
    
    instance.setAuthorizationStrategy(strategy)
    println "✅ Authorization strategy configured"
    
    // Force save configuration
    instance.save()
    println "✅ Configuration saved to disk"
    
    println "🎉 OpenID Connect authentication configured successfully!"
    println "   - OIDC URL: https://cognito-idp.us-east-1.amazonaws.com/${cognitoUserPoolId}/.well-known/openid-configuration"
    println "   - Client ID: ${cognitoClientId}"
    println "   - Scopes: openid email profile"
    println ""
    println "🔗 Access Jenkins at: http://localhost:8080"
    println "   You should see 'Login with Openid Connect' option"
    
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