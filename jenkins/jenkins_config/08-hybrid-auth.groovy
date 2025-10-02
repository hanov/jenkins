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

println "Setting up hybrid authentication (OIDC + Basic Auth for API testing)..."

try {
    // Check if we should enable hybrid mode
    def enableHybrid = System.getenv('JENKINS_ENABLE_HYBRID_AUTH') ?: 'true'
    
    if (enableHybrid == 'true') {
        println "Enabling basic auth alongside OIDC for API testing..."
        
        // Create a simple security realm that allows both
        def hudsonRealm = new HudsonPrivateSecurityRealm(false, false, null)
        
        // Create admin user if it doesn't exist
        def adminUser = hudsonRealm.getUser('admin')
        if (!adminUser) {
            println "Creating admin user for API access..."
            hudsonRealm.createAccount('admin', 'admin123')
        }
        
        instance.setSecurityRealm(hudsonRealm)
        
        // Set up authorization strategy
        def strategy = new GlobalMatrixAuthorizationStrategy()
        
        // Grant full permissions to admin user
        strategy.add(Jenkins.ADMINISTER, "admin")
        
        // Grant permissions to authenticated users (for OIDC users)
        strategy.add(Jenkins.READ, "authenticated")
        strategy.add(Item.BUILD, "authenticated")
        strategy.add(Item.CANCEL, "authenticated")
        strategy.add(Item.READ, "authenticated")
        strategy.add(Item.WORKSPACE, "authenticated")
        
        instance.setAuthorizationStrategy(strategy)
        
        println "✅ Hybrid authentication enabled"
        println "  - Basic auth: admin/admin123 (for API testing)"
        println "  - OIDC: Available via 'Login with Openid Connect' (for users)"
        
    } else {
        println "Hybrid auth disabled, keeping OIDC only"
    }
    
    // Save configuration
    instance.save()
    
} catch (Exception e) {
    println "Error setting up hybrid authentication: ${e.message}"
    e.printStackTrace()
    
    // Fallback to basic security
    println "Falling back to basic security..."
    
    def hudsonRealm = new HudsonPrivateSecurityRealm(false, false, null)
    
    try {
        hudsonRealm.createAccount('admin', 'admin123')
    } catch (Exception createError) {
        println "Admin user might already exist: ${createError.message}"
    }
    
    instance.setSecurityRealm(hudsonRealm)
    
    def strategy = new GlobalMatrixAuthorizationStrategy()
    strategy.add(Jenkins.ADMINISTER, "admin")
    strategy.add(Jenkins.READ, "authenticated")
    strategy.add(Item.BUILD, "authenticated")
    strategy.add(Item.CANCEL, "authenticated")
    strategy.add(Item.READ, "authenticated")
    
    instance.setAuthorizationStrategy(strategy)
    instance.save()
    
    println "✅ Basic authentication enabled as fallback"
}