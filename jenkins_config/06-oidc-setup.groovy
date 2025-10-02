#!groovy

import jenkins.model.*
import hudson.security.*
import org.kohsuke.stapler.StaplerRequest
import net.sf.json.JSONObject

def instance = Jenkins.getInstance()

println "Setting up OpenID Connect authentication for Jenkins with Cognito..."

// Cognito configuration
def cognitoDomain = "jenkins-auth-62745.auth.us-east-1.amazoncognito.com"
def cognitoUserPoolId = "us-east-1_mHHkRBGwp"
def cognitoClientId = "8suo93gn4lp3vm0dhv3prjtdn"
def cognitoClientSecret = "" // You'll need to set this if using client secret

try {
    // Set up authorization strategy first
    def strategy = new GlobalMatrixAuthorizationStrategy()
    
    // Grant permissions
    strategy.add(Jenkins.ADMINISTER, "admin")
    strategy.add(Jenkins.READ, "authenticated")
    strategy.add(Item.BUILD, "authenticated")
    strategy.add(Item.CANCEL, "authenticated")
    strategy.add(Item.READ, "authenticated")
    strategy.add(Item.WORKSPACE, "authenticated")
    
    instance.setAuthorizationStrategy(strategy)
    
    println "Authorization strategy configured for OIDC"
    
    // Note: The actual OIDC configuration needs to be done through Jenkins UI
    // after installing the OpenID Connect Authentication plugin
    
    instance.save()
    
    println """
OpenID Connect Setup Instructions:

1. Install OpenID Connect Authentication Plugin:
   - Go to Manage Jenkins > Manage Plugins
   - Search for 'OpenId Connect Authentication' and install it
   - Restart Jenkins

2. Configure Cognito App Client:
   - In AWS Cognito User Pool > App Integration > App clients
   - Create or edit your app client
   - Set callback URLs to include: http://localhost:8080/securityRealm/finishLogin
   - Enable OAuth 2.0 flows: Authorization code grant
   - OAuth scopes: openid, email, profile

3. Configure Jenkins OIDC:
   - Go to Manage Jenkins > Configure Global Security
   - Select 'Login with Openid Connect' as Security Realm
   - Client ID: ${cognitoClientId}
   - Client Secret: [Your Cognito App Client Secret]
   - Configuration mode: Automatic configuration
   - Well-known configuration endpoint: https://${cognitoDomain}/.well-known/openid_configuration
   - OR Manual configuration:
     - Token server URL: https://${cognitoDomain}/oauth2/token
     - Authorization server URL: https://${cognitoDomain}/oauth2/authorize
     - UserInfo server URL: https://${cognitoDomain}/oauth2/userInfo
     - JWKS server URL: https://cognito-idp.us-east-1.amazonaws.com/${cognitoUserPoolId}/.well-known/jwks.json
   - Scopes: openid email profile
   - User name field: preferred_username (or email)
   - Full name field: name
   - Email field: email

4. Test the configuration:
   - Save and logout of Jenkins
   - You should see 'Login with Openid Connect' button
   - Click it to authenticate via Cognito
"""

} catch (Exception e) {
    println "Error setting up OIDC foundation: ${e.message}"
    e.printStackTrace()
}