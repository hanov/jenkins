#!/usr/bin/env python3

import os
import json
import logging
import requests
import jenkins
from datetime import datetime, timedelta
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
import jwt
from functools import wraps
from slack_sdk.webhook import WebhookClient

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Configuration
JENKINS_URL = os.getenv('JENKINS_URL', 'http://jenkins:8080')
# Use Jenkins API token for authentication
JENKINS_USER = os.getenv('JENKINS_USER', '64d81418-a071-70bf-5d73-b2df0b046569')
JENKINS_PASS = os.getenv('JENKINS_PASS', '1110c3fd6a493c5a5b3c6c3749dc14301b')

COGNITO_DOMAIN = os.getenv('COGNITO_DOMAIN', 'jenkins-auth-62745.auth.us-east-1.amazoncognito.com')
COGNITO_USER_POOL_ID = os.getenv('COGNITO_USER_POOL_ID', 'us-east-1_mHHkRBGwp')
COGNITO_WEB_CLIENT_ID = os.getenv('COGNITO_WEB_CLIENT_ID', '8suo93gn4lp3vm0dhv3prjtdn')

# Slack Configuration
SLACK_WEBHOOK_TOKEN = os.getenv('SLACK_WEBHOOK_TOKEN')
SLACK_CHANNEL = os.getenv('SLACK_CHANNEL', 'jenkins-notifications')

# Jenkins client
jenkins_client = None

def get_jenkins_client():
    global jenkins_client
    if jenkins_client is None:
        try:
            jenkins_client = jenkins.Jenkins(JENKINS_URL, username=JENKINS_USER, password=JENKINS_PASS)
            # Test connection
            jenkins_client.get_whoami()
            logger.info(f"Connected to Jenkins at {JENKINS_URL}")
        except Exception as e:
            logger.error(f"Failed to connect to Jenkins: {e}")
            jenkins_client = None
    return jenkins_client

def send_slack_notification(job_name, build_number, cancelled_by, reason, timestamp):
    """Send Slack notification about build cancellation"""
    if not SLACK_WEBHOOK_TOKEN:
        logger.info("Slack webhook token not configured, skipping notification")
        return
    
    try:
        webhook = WebhookClient(url=SLACK_WEBHOOK_TOKEN)
        
        # Create rich message with blocks
        message_blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": "🛑 Jenkins Build Cancelled"
                }
            },
            {
                "type": "section",
                "fields": [
                    {
                        "type": "mrkdwn",
                        "text": f"*Job:* {job_name}"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Build #:* {build_number}"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Cancelled by:* {cancelled_by}"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Time:* {timestamp}"
                    }
                ]
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"*Reason:* {reason}"
                }
            }
        ]
        
        response = webhook.send(
            text=f"Build {job_name} #{build_number} cancelled by {cancelled_by}",
            channel=SLACK_CHANNEL,
            blocks=message_blocks
        )
        
        if response.status_code == 200:
            logger.info(f"Slack notification sent successfully for {job_name} #{build_number}")
        else:
            logger.error(f"Failed to send Slack notification: {response.status_code}")
            
    except Exception as e:
        logger.error(f"Error sending Slack notification: {e}")

def verify_cognito_token(token):
    """Verify Cognito JWT token and extract user info"""
    try:
        # Get Cognito JWKS
        jwks_url = f"https://cognito-idp.us-east-1.amazonaws.com/{COGNITO_USER_POOL_ID}/.well-known/jwks.json"
        jwks_response = requests.get(jwks_url)
        jwks = jwks_response.json()
        
        # Decode token header to get kid
        unverified_header = jwt.get_unverified_header(token)
        kid = unverified_header['kid']
        
        # Get unverified payload for debugging
        unverified_payload = jwt.decode(token, options={"verify_signature": False})
        logger.info(f"Token type: {unverified_payload.get('token_use', 'unknown')}")
        logger.info(f"Token aud: {unverified_payload.get('aud', 'missing')}")
        logger.info(f"Token client_id: {unverified_payload.get('client_id', 'missing')}")
        logger.info(f"Token username: {unverified_payload.get('username', 'missing')}")
        logger.info(f"Token sub: {unverified_payload.get('sub', 'missing')}")
        
        # Find the correct key
        key = None
        for jwk in jwks['keys']:
            if jwk['kid'] == kid:
                key = jwt.algorithms.RSAAlgorithm.from_jwk(json.dumps(jwk))
                break
        
        if not key:
            raise ValueError("Unable to find appropriate key")
        
        # For access tokens, the audience is different from ID tokens
        token_use = unverified_payload.get('token_use')
        
        if token_use == 'access':
            # Access token - the audience is typically not the client_id
            # Try without audience verification first, then with client_id
            try:
                decoded_token = jwt.decode(
                    token,
                    key,
                    algorithms=['RS256'],
                    issuer=f'https://cognito-idp.us-east-1.amazonaws.com/{COGNITO_USER_POOL_ID}',
                    options={"verify_aud": False}
                )
            except Exception as e:
                logger.warning(f"Access token verification without audience failed: {e}")
                # Try with client_id as audience
                decoded_token = jwt.decode(
                    token,
                    key,
                    algorithms=['RS256'],
                    audience=COGNITO_WEB_CLIENT_ID,
                    issuer=f'https://cognito-idp.us-east-1.amazonaws.com/{COGNITO_USER_POOL_ID}'
                )
        elif token_use == 'id':
            # ID token - verify with client_id as audience
            decoded_token = jwt.decode(
                token,
                key,
                algorithms=['RS256'],
                audience=COGNITO_WEB_CLIENT_ID,
                issuer=f'https://cognito-idp.us-east-1.amazonaws.com/{COGNITO_USER_POOL_ID}'
            )
        else:
            # Try without audience verification for debugging
            decoded_token = jwt.decode(
                token,
                key,
                algorithms=['RS256'],
                issuer=f'https://cognito-idp.us-east-1.amazonaws.com/{COGNITO_USER_POOL_ID}',
                options={"verify_aud": False}
            )
        
        logger.info(f"Token verification successful for user: {decoded_token.get('username', 'unknown')}")
        return decoded_token
    except Exception as e:
        logger.error(f"Token verification failed: {e}")
        logger.error(f"Token payload (unverified): {unverified_payload if 'unverified_payload' in locals() else 'N/A'}")
        return None

def require_auth(f):
    """Decorator to require authentication - DISABLED FOR TESTING"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        # TEMPORARY: Skip auth verification for testing
        # Set a fake user for the request
        request.user = {
            'sub': 'test-user-123',
            'email': '4igo4ek@gmail.com',
            'username': 'test-user',
            'cognito:username': 'test-user'
        }
        return f(*args, **kwargs)
        
        # Original auth code (commented out for testing)
        """
        auth_header = request.headers.get('Authorization')
        if not auth_header or not auth_header.startswith('Bearer '):
            return jsonify({'error': 'No authorization token provided'}), 401
        
        token = auth_header.split(' ')[1]
        user_info = verify_cognito_token(token)
        
        if not user_info:
            return jsonify({'error': 'Invalid token'}), 401
        
        # Add user info to request context
        request.user = user_info
        return f(*args, **kwargs)
        """
    
    return decorated_function

def get_all_running_builds():
    """Get all running builds from Jenkins"""
    jenkins_conn = get_jenkins_client()
    if not jenkins_conn:
        return []
    
    all_builds = []
    try:
        # Get all jobs
        jobs = jenkins_conn.get_jobs()
        logger.info(f"Found {len(jobs)} jobs in Jenkins")
        
        for job in jobs:
            job_name = job['name']
            logger.info(f"Checking job: {job_name}")
            
            try:
                # Get job info with more details
                job_info = jenkins_conn.get_job_info(job_name)
                
                # Check if job has running indicator (color ends with _anime)
                job_color = job_info.get('color', '')
                logger.info(f"Job {job_name} color: {job_color}")
                
                # If job is running (color ends with _anime), get the running build
                if job_color.endswith('_anime'):
                    logger.info(f"Job {job_name} appears to be running (color: {job_color})")
                    
                    # Get the last build (which should be running)
                    last_build = job_info.get('lastBuild')
                    if last_build:
                        build_number = last_build['number']
                        logger.info(f"Checking last build #{build_number} for running status")
                        
                        try:
                            build_info = jenkins_conn.get_build_info(job_name, build_number)
                            building = build_info.get('building', False)
                            logger.info(f"Build {job_name}#{build_number}: building={building}")
                            
                            if building:
                                # Find who started the build
                                started_by = None
                                
                                # Check build causes for who started it
                                actions = build_info.get('actions', [])
                                for action in actions:
                                    if action.get('_class') == 'hudson.model.CauseAction':
                                        causes = action.get('causes', [])
                                        for cause in causes:
                                            if cause.get('_class') == 'hudson.model.Cause$UserIdCause':
                                                started_by = cause.get('userId', '')
                                                break
                                            elif cause.get('_class') == 'hudson.model.Cause$UserCause':
                                                started_by = cause.get('userName', '')
                                                break
                                        if started_by:
                                            break
                                
                                # If no specific user found, default to admin for test environment
                                if not started_by:
                                    started_by = 'admin'
                                
                                logger.info(f"Build {job_name}#{build_number} started by: {started_by}")
                                
                                # Add the running build
                                logger.info(f"Adding running build {job_name}#{build_number}")
                                all_builds.append({
                                    'job_name': job_name,
                                    'build_number': build_number,
                                    'started_by': started_by,
                                    'start_time': build_info.get('timestamp', 0),
                                    'url': build_info.get('url', ''),
                                    'estimated_duration': build_info.get('estimatedDuration', -1),
                                    'description': build_info.get('description', ''),
                                    'node': build_info.get('builtOn', 'built-in'),
                                    'display_name': build_info.get('fullDisplayName', f"{job_name} #{build_number}")
                                })
                        except Exception as e:
                            logger.warning(f"Error getting build info for {job_name}#{build_number}: {e}")
                            continue
                else:
                    logger.info(f"Job {job_name} is not running (color: {job_color})")
                        
            except Exception as e:
                logger.warning(f"Error getting job info for {job_name}: {e}")
                continue
                
    except Exception as e:
        logger.error(f"Error getting running builds: {e}")
    
    logger.info(f"Found {len(all_builds)} running builds total")
    return all_builds

@app.route('/')
def serve_index():
    """Serve the main page"""
    return send_from_directory('web', 'index.html')

@app.route('/<path:filename>')
def serve_static(filename):
    """Serve static files"""
    return send_from_directory('web', filename)

@app.route('/api/health')
def health_check():
    """Health check endpoint"""
    jenkins_conn = get_jenkins_client()
    jenkins_status = "connected" if jenkins_conn else "disconnected"
    
    return jsonify({
        'status': 'healthy',
        'jenkins': jenkins_status,
        'timestamp': datetime.utcnow().isoformat()
    })

@app.route('/api/user/builds')
@require_auth
def get_user_running_builds():
    """Get all running builds for any authenticated user"""
    try:
        # Extract username from token for display purposes
        username = (request.user.get('cognito:username') or 
                   request.user.get('username') or 
                   request.user.get('preferred_username') or
                   request.user.get('email', '').split('@')[0] if request.user.get('email') else None or
                   'unknown')
        
        if not username or username == 'admin':
            username = request.user.get('sub', 'unknown-user')[:8] if request.user.get('sub') else 'unknown'
        
        logger.info(f"Authenticated user: {username}")
        logger.info(f"Fetching all running builds...")
        logger.info(f"Available token fields: {list(request.user.keys())}")
        
        # Get all running builds regardless of who started them
        builds = get_all_running_builds()
        
        return jsonify({
            'builds': builds,
            'count': len(builds),
            'username': username,
            'message': 'Showing all running builds (any user can cancel any build)',
            'debug_user_info': request.user
        })
        
    except Exception as e:
        logger.error(f"Error getting running builds: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/api/builds/<job_name>/<int:build_number>/cancel', methods=['POST'])
@require_auth
def cancel_build(job_name, build_number):
    """Cancel a specific build"""
    try:
        # Extract username from token - same logic as get_user_builds
        username = (request.user.get('cognito:username') or 
                   request.user.get('username') or 
                   request.user.get('preferred_username') or
                   request.user.get('email', '').split('@')[0] if request.user.get('email') else None or
                   'admin')
        
        if not username or username == 'admin':
            username = request.user.get('sub', 'unknown-user')[:8] if request.user.get('sub') else 'admin'
        
        if not username:
            return jsonify({'error': 'Unable to determine username'}), 400
        
        # Get cancellation reason from request
        data = request.get_json()
        reason = data.get('reason', '').strip() if data else ''
        
        if not reason:
            return jsonify({'error': 'Cancellation reason is required'}), 400
        
        jenkins_conn = get_jenkins_client()
        if not jenkins_conn:
            return jsonify({'error': 'Jenkins connection failed'}), 503
        
        # Verify the build exists and is running
        try:
            build_info = jenkins_conn.get_build_info(job_name, build_number)
            
            if not build_info.get('building', False):
                return jsonify({'error': 'Build is not currently running'}), 400
            
            # Verify user owns this build
            actions = build_info.get('actions', [])
            started_by = None
            
            for action in actions:
                if action.get('_class') == 'hudson.model.CauseAction':
                    causes = action.get('causes', [])
                    for cause in causes:
                        if cause.get('_class') == 'hudson.model.Cause$UserIdCause':
                            started_by = cause.get('userId', '')
                            break
                        elif cause.get('_class') == 'hudson.model.Cause$UserCause':
                            started_by = cause.get('userName', '')
                            break
                    if started_by:
                        break
            
            # If no specific user found, default to admin for test environment
            if not started_by:
                started_by = 'admin'
            
            # Allow any authenticated user to cancel any build for testing purposes
            logger.info(f"User {username} attempting to cancel build started by {started_by}")
            # Skip ownership check - any authenticated user can cancel any build
            
        except jenkins.NotFoundException:
            return jsonify({'error': 'Build not found'}), 404
        
        # Cancel the build
        try:
            jenkins_conn.stop_build(job_name, build_number)
            
            # Log the cancellation
            logger.info(f"Build {job_name}#{build_number} cancelled by {username}. Reason: {reason}")
            
            # Send Slack notification
            timestamp = datetime.utcnow().isoformat()
            send_slack_notification(job_name, build_number, username, reason, timestamp)
            
            return jsonify({
                'success': True,
                'message': f'Build {job_name}#{build_number} has been cancelled',
                'job_name': job_name,
                'build_number': build_number,
                'cancelled_by': username,
                'reason': reason,
                'timestamp': timestamp
            })
            
        except Exception as e:
            logger.error(f"Failed to cancel build {job_name}#{build_number}: {e}")
            return jsonify({'error': 'Failed to cancel build'}), 500
        
    except Exception as e:
        logger.error(f"Error in cancel_build: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/api/user/info')
@require_auth
def get_user_info():
    """Get authenticated user information"""
    # Use same username extraction logic
    username = (request.user.get('cognito:username') or 
               request.user.get('username') or 
               request.user.get('preferred_username') or
               request.user.get('email', '').split('@')[0] if request.user.get('email') else None or
               'admin')
    
    if not username or username == 'admin':
        username = request.user.get('sub', 'unknown-user')[:8] if request.user.get('sub') else 'admin'
    
    return jsonify({
        'user': request.user,
        'username': username,
        'available_fields': list(request.user.keys())
    })

@app.route('/api/debug/token', methods=['POST'])
def debug_token():
    """Debug endpoint to check token content"""
    try:
        auth_header = request.headers.get('Authorization')
        if not auth_header or not auth_header.startswith('Bearer '):
            return jsonify({'error': 'No authorization token provided'}), 401
        
        token = auth_header.split(' ')[1]
        
        # Decode without verification for debugging
        unverified_payload = jwt.decode(token, options={"verify_signature": False})
        
        return jsonify({
            'token_type': unverified_payload.get('token_use', 'unknown'),
            'audience': unverified_payload.get('aud', 'missing'),
            'client_id': unverified_payload.get('client_id', 'missing'),
            'scope': unverified_payload.get('scope', 'missing'),
            'username': unverified_payload.get('username', 'missing'),
            'sub': unverified_payload.get('sub', 'missing'),
            'iss': unverified_payload.get('iss', 'missing'),
            'exp': unverified_payload.get('exp', 'missing')
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 400

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)