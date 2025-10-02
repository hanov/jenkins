#!groovy

import jenkins.model.*
import hudson.security.*
import hudson.util.Secret

def instance = Jenkins.getInstance()

def cognitoDomain = System.getenv('COGNITO_DOMAIN')
def clientId = System.getenv('COGNITO_CLIENT_ID')
def clientSecret = System.getenv('COGNITO_CLIENT_SECRET')
def userPoolId = System.getenv('COGNITO_USER_POOL_ID')
def awsRegion = System.getenv('AWS_REGION') ?: 'us-east-1'

println "Cognito Domain: ${cognitoDomain}"
println "Client ID: ${clientId}"
println "Client Secret: ${clientSecret ? 'SET' : 'NOT SET'}"

if (cognitoDomain && clientId && clientSecret) {
    try {
        def oicSecurityRealm = Class.forName('org.jenkinsci.plugins.oic.OicSecurityRealm')
        def oicServerConfig = Class.forName('org.jenkinsci.plugins.oic.OicServerConfiguration')
        
        def serverConfig = oicServerConfig.newInstance(
            "https://${cognitoDomain}/oauth2/authorize",
            "https://${cognitoDomain}/oauth2/token",
            "https://${cognitoDomain}/oauth2/userInfo",
            null,
            "sub",
            "email",
            "name",
            "openid email profile",
            null,
            null,
            null,
            null,
            null
        )
        
        def oicRealm = oicSecurityRealm.newInstance(
            clientId,
            Secret.fromString(clientSecret),
            serverConfig,
            false,
            null,
            null
        )
        
        instance.setSecurityRealm(oicRealm)
        println "Cognito OIDC authentication configured successfully"
    } catch (Exception e) {
        println "Failed to configure Cognito OIDC: ${e.message}"
        e.printStackTrace()
        
        def hudsonRealm = new HudsonPrivateSecurityRealm(false)
        def username = System.getenv('JENKINS_USER') ?: 'admin'
        def password = System.getenv('JENKINS_PASS') ?: 'admin123'
        
        hudsonRealm.createAccount(username, password)
        instance.setSecurityRealm(hudsonRealm)
        println "Fallback to local authentication due to OIC configuration error"
    }
} else {
    def hudsonRealm = new HudsonPrivateSecurityRealm(false)
    def username = System.getenv('JENKINS_USER') ?: 'admin'
    def password = System.getenv('JENKINS_PASS') ?: 'admin123'
    
    hudsonRealm.createAccount(username, password)
    instance.setSecurityRealm(hudsonRealm)
    println "Fallback to local authentication - Cognito env vars not complete"
}

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)

instance.save()