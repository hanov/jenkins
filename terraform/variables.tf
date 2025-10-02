variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "user_pool_name" {
  description = "Name of the Cognito User Pool"
  type        = string
  default     = "jenkins-users"
}

variable "client_name" {
  description = "Name of the Cognito User Pool Client"
  type        = string
  default     = "jenkins-client"
}

variable "domain_prefix" {
  description = "Domain prefix for Cognito hosted UI (random suffix will be added)"
  type        = string
  default     = "jenkins-auth"
  
  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.domain_prefix))
    error_message = "Domain prefix must contain only lowercase letters, numbers, and hyphens."
  }
}

variable "callback_urls" {
  description = "List of allowed callback URLs"
  type        = list(string)
  default     = [
    "http://localhost/securityRealm/finishLogin",
    "http://localhost:8080/securityRealm/finishLogin"
  ]
}

variable "logout_urls" {
  description = "List of allowed logout URLs"
  type        = list(string)
  default     = [
    "http://localhost",
    "http://localhost:8080"
  ]
}

variable "create_admin_user" {
  description = "Whether to create an admin user"
  type        = bool
  default     = true
}

variable "admin_username" {
  description = "Admin user username"
  type        = string
  default     = "admin"
}

variable "admin_email" {
  description = "Admin user email"
  type        = string
  default     = "admin@example.com"
}

variable "admin_name" {
  description = "Admin user display name"
  type        = string
  default     = "Jenkins Administrator"
}

variable "admin_temp_password" {
  description = "Temporary password for admin user"
  type        = string
  default     = "TempPass123!"
  sensitive   = true
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default = {
    Environment = "development"
    Project     = "jenkins-auth"
    ManagedBy   = "terraform"
  }
}