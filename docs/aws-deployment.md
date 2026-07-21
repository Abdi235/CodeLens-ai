# AWS deployment sketch for SecureAI

This is a practical Week-4 blueprint. It is intentionally lightweight so you can
deploy after installing Docker Desktop + configuring AWS CLI credentials.

## Target architecture

```
React (S3 + CloudFront)  OR  EC2/nginx frontend container
        |
   API Gateway  (optional) / ALB
        |
 Spring Boot on EC2 / ECS
        |
   +----+----+
   |         |
 RDS PG   AI service (ECS/EC2)
              |
         Semgrep + LLM
```

## Suggested services

| Concern | AWS service |
| --- | --- |
| API | EC2 or ECS Fargate |
| DB | RDS PostgreSQL |
| Artifacts / zip uploads | S3 |
| Scan workers | Lambda (zip scans) or ECS tasks |
| Secrets | Secrets Manager (`JWT_SECRET`, `OPENAI_API_KEY`, DB creds) |
| Observability | CloudWatch logs + alarms on `/actuator/health` |

## Minimal EC2 bootstrap

1. Launch Amazon Linux 2023, open ports 22/80/443/8080/8000 as needed.
2. Install Docker.
3. Copy `docker-compose.yml` + build contexts.
4. Set env:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_*` to RDS endpoint
   - `JWT_SECRET`
   - `OPENAI_API_KEY` (optional)
5. `docker compose up -d --build`

## Terraform starter (optional next step)

Create `infra/terraform/` with:

- `aws_db_instance` (Postgres)
- `aws_ecs_cluster` + task defs for backend/ai-service
- `aws_s3_bucket` for uploads/reports
- `aws_lb` + target groups

Keep state in an S3 backend with locking.

## CI/CD note

GitHub Actions already builds/tests on push. Add a deploy job that:

1. Builds images
2. Pushes to ECR
3. Updates ECS service

Only wire deploy after AWS credentials are available in repository secrets.
