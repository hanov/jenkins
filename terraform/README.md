# Terraform AWS Cognito for Jenkins

This Terraform configuration provisions an AWS Cognito User Pool for Jenkins authentication.

## Resources Created

- **Cognito User Pool**: Main user directory
- **Cognito User Pool Client**: OAuth client for Jenkins
- **Cognito Domain**: Hosted UI domain for authentication
- **Admin User**: Optional initial admin user

## Prerequisites

1. **AWS CLI configured** with appropriate credentials
2. **Terraform installed** (version >= 1.0)
3. **AWS permissions** for Cognito resources

## Usage

### 1. Initialize Terraform
```bash
cd terraform
terraform init
```

### 2. Configure Variables
```bash
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values
```

**Important**: Update `domain_prefix` to be globally unique (e.g., `your-company-jenkins-auth`)

### 3. Plan and Apply
```bash
# Review the plan
terraform plan

# Apply the configuration
terraform apply
```

### 4. Get Outputs
```bash
# Get all outputs
terraform output

# Get specific output (sensitive)
terraform output -raw client_secret

# Get Jenkins environment variables
terraform output jenkins_env_vars
```

### 5. Update Jenkins Configuration

Copy the output values to your Jenkins `.env` file:

```bash
# Get the values from terraform output
terraform output jenkins_env_vars
```

## Configuration Options

### Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `aws_region` | AWS region | `us-east-1` |
| `user_pool_name` | Cognito User Pool name | `jenkins-users` |
| `domain_prefix` | Domain prefix (must be unique) | `jenkins-auth` |
| `callback_urls` | OAuth callback URLs | Local URLs |
| `admin_email` | Admin user email | `admin@example.com` |

### Callback URLs

Update `callback_urls` in `terraform.tfvars` to match your Jenkins URLs:

```hcl
callback_urls = [
  "https://jenkins.yourcompany.com/securityRealm/finishLogin"
]
```

## Outputs

- `user_pool_id`: Cognito User Pool ID
- `client_id`: OAuth Client ID  
- `client_secret`: OAuth Client Secret (sensitive)
- `cognito_domain`: Full Cognito domain URL
- `jenkins_env_vars`: All environment variables for Jenkins

## Cleanup

```bash
terraform destroy
```

## Security Notes

- Client secret is marked as sensitive
- Admin temporary password should be changed immediately
- Use HTTPS callbacks in production
- Enable MFA for production environments

## Troubleshooting

### Domain Already Exists
If you get "domain already exists" error, change the `domain_prefix` variable to something unique.

### Permission Denied
Ensure your AWS credentials have the following permissions:
- `cognito-idp:*`
- `iam:PassRole` (if using custom roles)