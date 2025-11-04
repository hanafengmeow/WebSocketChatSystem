# WebSocket Chat System with SQS Integration

A distributed WebSocket chat system built with Spring Boot, AWS SQS, and AWS infrastructure, designed for high-throughput message processing and horizontal scalability.

## Overview

This system implements a real-time chat application with three main components:

- **Client**: Simulates thousands of users sending messages via WebSocket
- **Server**: Handles WebSocket connections, validates messages, and publishes to SQS
- **Consumer**: Polls SQS queues and broadcasts messages back to clients via STOMP

## Architecture Highlights

<img src="https://raw.githubusercontent.com/hanafengmeow/WebSocketChatSystem/main/assert/SystemStructure.jpg"
     alt="Design Architecture Diagram" width="1070" height="722">

- **Queue-Based Architecture**: Uses AWS SQS for reliable message delivery between server and consumer
- **Load Balancing**: Application Load Balancer (ALB) distributes WebSocket connections across multiple server instances
- **Auto Scaling**: Auto Scaling Group dynamically adjusts server capacity based on CPU utilization
- **Dead Letter Queue**: Failed messages are automatically sent to DLQ for manual inspection
- **Service Registry**: DynamoDB-based registry for service discovery between servers and consumers
- **CloudWatch Integration**: Comprehensive metrics collection for monitoring and debugging

## Key Features

- Three-queue message sending system (JOIN, TEXT, LEAVE) with dependency tracking
- Per-room SQS queues with dedicated consumer polling threads
- STOMP-based message broadcasting from consumer to server
- Graceful error handling with retry logic and DLQ support
- Real-time metrics and statistics tracking
- Horizontal scaling with load balancing

## Quick Start

### Prerequisites

- Java 17+
- Gradle 7+
- AWS CLI configured with appropriate credentials
- Node.js and npm (for CDK deployment)
- AWS CDK CLI installed globally

### Build

```bash
./gradlew build
```

### Deploy Infrastructure

The CDK stack automatically deploys all AWS resources and configures EC2 instances:

```bash
cd cdk
npm install
cd ..
./gradlew cdk-bootstrap  # First time only - bootstrap CDK environment
./gradlew cdk-deploy     # Deploy all infrastructure
```

**What Gets Deployed Automatically:**

1. **Infrastructure Resources:**
   - VPC with public subnets
   - Security groups for ALB, Server, and Consumer
   - S3 bucket for application deployment
   - DynamoDB table for service registry
   - SQS queues (created on-demand)

2. **Application Load Balancer (ALB):**
   - Internet-facing ALB on port 80
   - Target group for server instances
   - Health check configuration
   - Sticky session support

3. **Auto Scaling Group (Server Instances):**
   - Deploys 2-10 t3.micro instances automatically
   - Downloads JAR from S3 on startup
   - Configures Spring Boot with `server` profile
   - Starts application automatically on port 8080
   - Auto-scales based on CPU utilization (target: 70%)

4. **Consumer EC2 Instance:**
   - Single t3.micro instance
   - Downloads JAR from S3 on startup
   - Configures Spring Boot with `consumer` profile
   - Starts application automatically on port 9090
   - Begins polling SQS queues immediately

**Automatic Configuration:**

The CDK uses EC2 User Data scripts to automatically:
- Install Java 17
- Download application JAR from S3
- Install CloudWatch agent
- Configure application properties
- Start Spring Boot application with correct profile
- Set up systemd service for auto-restart

**Deployment Output:**

After deployment, CDK outputs:
- ALB DNS name (e.g., `ChatSy-WebSo-xxx.us-east-1.elb.amazonaws.com`)
- WebSocket endpoint URL (e.g., `ws://ChatSy-WebSo-xxx.us-east-1.elb.amazonaws.com/chat/{roomid}`)

### Run Components

**Client (Local):**
```bash
./gradlew bootRun --args='--spring.profiles.active=client'
```

**Server and Consumer:**
- **Automatically deployed and running** on EC2 instances after `cdk-deploy`
- No manual steps required - instances start automatically
- Check CloudWatch logs or EC2 console to verify instances are running

## Configuration

Configuration files are located in `src/main/resources/`:

- `application.properties` - Common configuration
- `application-client.properties` - Client settings
- `application-server.properties` - Server settings  
- `application-consumer.properties` - Consumer settings

Key configuration parameters:

- Queue capacities and thread pool sizes
- SQS polling intervals and retry settings
- CloudWatch metrics namespaces
- Load balancer and auto scaling parameters

## Testing

The client application simulates load testing with configurable:
- Number of users (default: 10,000)
- Number of rooms (default: 20)
- Total messages (default: 50,000)

Results are logged every 5 seconds showing:
- Queue usage percentages
- Messages sent, ACK received, broadcast received
- Throughput metrics

## Project Structure

```
src/main/java/edu/northeastern/hanafeng/chatsystem/
├── client/          # Load testing client
├── server/          # WebSocket server
├── consumer/        # SQS message consumer
├── common/          # Shared services and utilities
└── model/           # Data models

cdk/                 # Infrastructure as Code (AWS CDK)
├── lib/constructs/  # CDK constructs
└── resources/       # EC2 user data scripts
```

## AWS Resources

- **VPC**: Isolated network for EC2 instances
- **ALB**: Load balancer for WebSocket connections
- **ASG**: Auto Scaling Group for server instances (2-10 instances)
- **EC2**: Consumer instance (t3.micro)
- **SQS**: Per-room queues + DLQ
- **DynamoDB**: Service registry table
- **CloudWatch**: Metrics and logging
- **S3**: Application deployment bucket

## Metrics

CloudWatch metrics are published for:
- Client: MessageSent, AckReceived, BroadcastReceived, MessageFailed
- Server: SuccessfulMessages, FailedMessages, BroadcastSuccess, BroadcastFailure
- Consumer: BroadcastMessages, Queue processing metrics

## Deployment Commands

**Full Deployment:**
```bash
./gradlew cdk-deploy
```

**Destroy All Resources:**
```bash
./gradlew cdk-destroy
```

**Redeploy (Destroy + Deploy):**
```bash
./gradlew cdk-redeploy
```

**Check Deployment Status:**
```bash
cd cdk
cdk list          # List all stacks
cdk diff          # Show changes before deployment
cdk synth         # Generate CloudFormation template
```

**Note:** The `cdk-destroy` command also automatically cleans up SQS queues via a Lambda function before stack deletion.

## Documentation

For detailed architecture, design decisions, and test results, see **Wiki**

## License

This project is created for educational purposes as part of CS6650 Distributed Systems course.

