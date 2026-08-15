# JobTracker

Automated job scraping and matching system for Java backend developer positions.

## Features

- 🤖 **AI-Powered Matching**: Uses AWS Bedrock (Claude Haiku 4.5) for intelligent job analysis
- 🎯 **Smart Filtering**: Filters by location, experience level, and skills
- 📱 **Telegram Notifications**: Real-time job alerts with match scores
- 🔄 **Multi-Platform**: Supports 6 major companies (AmEx, JPMorgan, Barclays, Goldman Sachs, Microsoft, Visa)
- 🗄️ **MongoDB Storage**: Automatic deduplication and job tracking
- 🐳 **Docker Ready**: Easy deployment with Docker

## Quick Start

Follow these steps after cloning the repo to build and run JobTracker locally.

### Prerequisites

| Requirement | Notes |
|-------------|--------|
| **Java 17+** | Required to run the app |
| **Maven** | Use the included wrapper: `./mvnw` (no global Maven install needed) |
| **MongoDB** | Must be running before the app starts |
| **Telegram bot** | Bot token + chat ID for job notifications |
| **AWS Bedrock** | Optional for local dev — mock AI mode is enabled by default |

Optional (for resume PDF generation):
- **TeX Live** (`pdflatex`) for local LaTeX compilation, or use the online fallback

### 1. Clone the repository

```bash
git clone <repository-url>
cd JobTracker
```

### 2. Start MongoDB

Using Docker (recommended):

```bash
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

Or point `MONGO_URI` at any existing MongoDB instance.

Default connection: `mongodb://localhost:27017/jobtracker`

### 3. Configure credentials

Copy the example env file and fill in your values:

```bash
cp .env.example .env.local
# Edit .env.local with your Telegram (and optionally AWS) credentials
nano .env.local
```

Load environment variables into your shell:

```bash
export $(grep -v '^#' .env.local | xargs)
```

**Minimum required for notifications:**

```bash
export TELEGRAM_BOT_TOKEN="your_bot_token"
export TELEGRAM_CHAT_ID="your_chat_id"
```

**AWS credentials** are only needed if you disable mock AI mode (see below).

> **Security:** Never commit `.env.local` or real credentials to Git. Use environment variables only.

### 4. Build the project

```bash
./mvnw clean install
```

### 5. Run the application (dev profile)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or run the packaged JAR:

```bash
java -jar target/jobtracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### 6. Verify it is running

```bash
curl http://localhost:8080/health
# Expected: JobTracker is running
```

On startup you should see:

```
========== JobTracker STARTED at ... ==========
```

The scheduler waits **10 seconds**, then runs job checks every **5 minutes** (after each run completes).

### Profiles

| Profile | Use case | Config file |
|---------|----------|-------------|
| **dev** | Local development (mock AI enabled by default) | `application-dev.properties` |
| **prod** | Production, Docker, Render | `application-prod.properties` |
| *(default)* | Env var fallbacks | `application.properties` |

### Local dev without AWS costs

The dev profile enables **mock AI** by default (`AI_DEV_MOCK_ENABLED=true`), so you can test job scraping and notifications without calling AWS Bedrock.

To use real AI scoring locally:

```bash
export AI_DEV_MOCK_ENABLED=false
export AWS_ACCESS_KEY_ID="your_access_key"
export AWS_SECRET_ACCESS_KEY="your_secret_key"
```

### Run a job check immediately (optional)

By default, jobs run on the scheduler only. To trigger a scan on startup, uncomment a `CommandLineRunner` bean in `JobtrackerApplication.java`.

### Docker Deployment

```bash
# Build image
docker build -t jobtracker:latest .

# Run with environment variables
docker run -d \
  -p 8080:8080 \
  -e MONGO_URI="mongodb://host.docker.internal:27017/jobtracker" \
  -e TELEGRAM_BOT_TOKEN="your_token" \
  -e TELEGRAM_CHAT_ID="your_chat_id" \
  -e AWS_ACCESS_KEY_ID="your_access_key" \
  -e AWS_SECRET_ACCESS_KEY="your_secret_key" \
  --name jobtracker \
  jobtracker:latest
```

The Docker image uses the **prod** profile and includes LaTeX for resume PDF generation.

### Common issues

| Issue | Fix |
|-------|-----|
| MongoDB connection failed | Ensure MongoDB is running on `localhost:27017` |
| No Telegram messages | Verify `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set |
| AWS / Bedrock errors | Keep `AI_DEV_MOCK_ENABLED=true` for local testing |
| Resume PDF fails | Install TeX Live (`pdflatex`) or set `LATEX_LOCAL_ENABLED=false` |

### Render.com Deployment

1. **Create new Web Service** on Render.com
2. **Connect your Git repository**
3. **Set Build Command**: `./mvnw clean install -DskipTests`
4. **Set Start Command**: `java -jar target/jobtracker-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
5. **Add Environment Variables**:
   ```
   MONGO_URI=<your_mongodb_atlas_uri>
   TELEGRAM_BOT_TOKEN=<your_bot_token>
   TELEGRAM_CHAT_ID=<your_chat_id>
   AWS_BEDROCK_REGION=us-east-1
   AWS_ACCESS_KEY_ID=<your_aws_access_key>
   AWS_SECRET_ACCESS_KEY=<your_aws_secret_key>
   AWS_BEDROCK_MODEL_ID=us.anthropic.claude-haiku-4-5-20251001-v1:0
   ```

## Configuration

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `TELEGRAM_BOT_TOKEN` | Your Telegram bot token | Yes | - |
| `TELEGRAM_CHAT_ID` | Your Telegram chat ID | Yes | - |
| `MONGO_URI` | MongoDB connection string | Yes | - |
| `AWS_ACCESS_KEY_ID` | AWS access key | Yes | - |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | Yes | - |
| `AWS_BEDROCK_REGION` | AWS region | No | us-east-1 |
| `AWS_BEDROCK_MODEL_ID` | Claude model ID | No | us.anthropic.claude-haiku-4-5-20251001-v1:0 |
| `AI_DEV_MOCK_ENABLED` | Use mock AI instead of AWS Bedrock (dev) | No | `true` |
| `AI_BEDROCK_ENABLED` | Enable/disable Bedrock AI scoring | No | `true` |
| `RESUME_TAILORING_ENABLED` | Enable AI resume tailoring | No | `true` |
| `LATEX_LOCAL_ENABLED` | Compile PDFs locally with pdflatex | No | `true` |

### Candidate Profile

Edit your profile in `application-dev.properties` or `application-prod.properties`:

```properties
candidate.skills=Java,Spring Boot,Microservices,REST API,Kafka,SQL
candidate.experience=3
candidate.roles=Software Engineer,Backend Engineer,Java Developer
```

## Project Structure

```
JobTracker/
├── src/main/java/com/jobtracker/jobtracker/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST controllers
│   ├── model/          # Domain models
│   ├── repository/     # MongoDB repositories
│   ├── scheduler/      # Scheduled jobs
│   ├── service/        # Business logic
│   └── provider/       # Job provider integrations
├── src/main/resources/
│   ├── application.properties
│   ├── application-dev.properties
│   └── application-prod.properties
├── .env.example        # Environment variables template
├── .env.local          # Local credentials (gitignored)
└── Dockerfile          # Docker configuration
```

## Architecture

- **Platform Handlers**: Strategy pattern for different job platforms (Oracle HCM, Workday, GraphQL)
- **Dynamic Providers**: Configuration-driven provider registration
- **AI Integration**: AWS Bedrock for intelligent job matching
- **Concurrent Processing**: Configurable thread pools with rate limiting
- **Resilient Design**: 3-tier fallback system (AI → Structured → Fuzzy matching)

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 4.0.5
- **Database**: MongoDB
- **AI/ML**: AWS Bedrock (Claude Haiku 4.5)
- **Build Tool**: Maven 3.9.6
- **Containerization**: Docker

## Health Check

```bash
curl http://localhost:8080/health
# Response: "JobTracker is running"
```

## Security Notes

⚠️ **Never commit sensitive data to Git!**

- All credentials are stored in `.env.local` (gitignored)
- Use environment variables for all secrets
- `.env.example` provides template without actual values
- `application-dev.properties` and `application-prod.properties` use environment variable substitution

## Cost Estimate

- **AWS Bedrock**: ~$1.50/month (50 jobs/day with Claude Haiku 4.5)
- **MongoDB Atlas**: Free tier or ~$9/month
- **Render.com**: Free tier or ~$7/month

**Total**: ~$17.50/month (or ~$1.50/month with local MongoDB)

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## License

This is a personal project for job hunting automation. Not licensed for commercial use.

## Support

For issues or questions, please open an issue on GitHub.

---

**Built with ❤️ for automating the job hunt**