output "user_pool_id" {
  description = "ID of the Cognito User Pool"
  value       = aws_cognito_user_pool.jenkins_users.id
}

output "user_pool_arn" {
  description = "ARN of the Cognito User Pool"
  value       = aws_cognito_user_pool.jenkins_users.arn
}

output "user_pool_endpoint" {
  description = "Endpoint name of the user pool"
  value       = aws_cognito_user_pool.jenkins_users.endpoint
}

output "client_id" {
  description = "ID of the Cognito User Pool Client"
  value       = aws_cognito_user_pool_client.jenkins_client.id
}

output "client_secret" {
  description = "Secret of the Cognito User Pool Client"
  value       = aws_cognito_user_pool_client.jenkins_client.client_secret
  sensitive   = true
}

output "web_client_id" {
  description = "ID of the Web Cognito User Pool Client (no secret)"
  value       = aws_cognito_user_pool_client.web_client.id
}

output "cognito_domain" {
  description = "Cognito User Pool Domain"
  value       = aws_cognito_user_pool_domain.jenkins_domain.domain
}

output "cognito_domain_full" {
  description = "Full Cognito User Pool Domain URL"
  value       = "${aws_cognito_user_pool_domain.jenkins_domain.domain}.auth.${var.aws_region}.amazoncognito.com"
}

output "cognito_domain_cloudfront" {
  description = "CloudFront distribution for the Cognito domain"
  value       = aws_cognito_user_pool_domain.jenkins_domain.cloudfront_distribution_arn
}

output "authorization_endpoint" {
  description = "Authorization endpoint URL"
  value       = "https://${aws_cognito_user_pool_domain.jenkins_domain.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/authorize"
}

output "token_endpoint" {
  description = "Token endpoint URL"
  value       = "https://${aws_cognito_user_pool_domain.jenkins_domain.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/token"
}

output "userinfo_endpoint" {
  description = "User info endpoint URL"
  value       = "https://${aws_cognito_user_pool_domain.jenkins_domain.domain}.auth.${var.aws_region}.amazoncognito.com/oauth2/userInfo"
}

output "jenkins_env_vars" {
  description = "Environment variables for Jenkins .env file"
  value = {
    COGNITO_USER_POOL_ID = aws_cognito_user_pool.jenkins_users.id
    COGNITO_CLIENT_ID    = aws_cognito_user_pool_client.jenkins_client.id
    COGNITO_CLIENT_SECRET = aws_cognito_user_pool_client.jenkins_client.client_secret
    COGNITO_DOMAIN       = "${aws_cognito_user_pool_domain.jenkins_domain.domain}.auth.${var.aws_region}.amazoncognito.com"
    AWS_REGION          = var.aws_region
  }
  sensitive = true
}