#!groovy

import jenkins.model.*
import hudson.security.*

// This script runs EVERY TIME Jenkins starts to ensure OIDC config is applied
def instance = Jenkins.getInstance()

// Get environment variables
def cognitoUserPoolId = System.getenv('COGNITO_USER_POOL_ID') ?: 'us-east-1_mHHkRBGwp'
def cognitoClientId = System.getenv('COGNITO_CLIENT_ID') ?: '8suo93gn4lp3vm0dhv3prjtdn'
def cognitoClientSecret = System.getenv('COGNITO_CLIENT_SECRET') ?: ''

println "🔄 STARTUP: Ensuring OIDC configuration is applied..."
println "User Pool ID: ${cognitoUserPoolId}"
println "Client ID: ${cognitoClientId}"

// Always ensure OIDC is configured on startup
try {
    def oicPlugin = instance.getPlugin('oic-auth')
    if (oicPlugin) {
        println "✅ OIDC plugin available, applying configuration..."
        
        // Configure OIDC Security Realm
        def securityRealm = new org.jenkinsci.plugins.oic.OicSecurityRealm(
            cognitoClientId,
            cognitoClientSecret,
            "https://cognito-idp.us-east-1.amazonaws.com/${cognitoUserPoolId}/.well-known/openid-configuration",
            "openid email profile",
            false,  // disableSslVerification
            false,  // logoutFromOpenidProvider
            "preferred_username",  // userNameField
            "name", // fullNameFieldName
            "email", // emailFieldName
            null,   // groupsFieldName
            false,  // escapeHatchEnabled
            null,   // escapeHatchUsername
            null,   // escapeHatchSecret
            null,   // escapeHatchGroup
            "auto"  // automanualconfigure
        )
        
        instance.setSecurityRealm(securityRealm)
        
        // Set authorization strategy
        def strategy = new GlobalMatrixAuthorizationStrategy()
        strategy.add(Jenkins.READ, "authenticated")
        strategy.add(Item.BUILD, "authenticated")
        strategy.add(Item.CANCEL, "authenticated")
        strategy.add(Item.READ, "authenticated")
        strategy.add(Item.WORKSPACE, "authenticated")
        strategy.add(Jenkins.ADMINISTER, "admin")
        strategy.add(Jenkins.ADMINISTER, "user-jenkins")
        strategy.add(Jenkins.ADMINISTER, "64d81418-a071-70bf-5d73-b2df0b046569")
        
        instance.setAuthorizationStrategy(strategy)
        instance.save()
        
        println "✅ OIDC configuration applied and saved on startup"
    } else {
        println "❌ OIDC plugin not available"
    }
} catch (Exception e) {
    println "❌ Error applying OIDC config on startup: ${e.message}"
}