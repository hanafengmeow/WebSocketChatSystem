JAR_FILE="<%= JAR_FILE %>"
BUCKET_NAME="<%= BUCKET_NAME %>"

echo "Starting user data script for consumer..."

# Update system
echo "Updating system..."
yum update -y

# Install Java 17
echo "Installing Java 17..."
yum install -y java-17-amazon-corretto-headless

# Verify Java installation
java -version

# Install CloudWatch agent
echo "Installing CloudWatch agent..."
yum install -y amazon-cloudwatch-agent

# Create application directory
echo "Creating application directory..."
mkdir -p /opt/websocketchat-consumer
cd /opt/websocketchat-consumer

# Download JAR and CloudWatch config from S3
echo "Downloading JAR from S3..."
aws s3 cp s3://$BUCKET_NAME/jars/$JAR_FILE .

echo "Downloading CloudWatch config from S3..."
aws s3 cp s3://$BUCKET_NAME/consumer/cloudwatch-config.json /opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json

# Create logs directory
echo "Creating logs directory..."
mkdir -p /var/log/websocketchat-consumer
chown ec2-user:ec2-user /var/log/websocketchat-consumer

# Create systemd service with variable expansion
echo "Creating systemd service..."
cat > /etc/systemd/system/websocketchat-consumer.service << EOF
[Unit]
Description=WebSocket Chat Consumer
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/opt/websocketchat-consumer
ExecStart=/usr/bin/java -jar /opt/websocketchat-consumer/$JAR_FILE --spring.profiles.active=consumer
StandardOutput=append:/var/log/websocketchat-consumer/application.log
StandardError=append:/var/log/websocketchat-consumer/application.log
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Change ownership
echo "Changing ownership..."
chown -R ec2-user:ec2-user /opt/websocketchat-consumer

# Enable and start service
echo "Enabling and starting service..."
systemctl daemon-reload
systemctl enable websocketchat-consumer
systemctl start websocketchat-consumer

# Wait for service to start
sleep 10

# Check service status
echo "Service status:"
systemctl status websocketchat-consumer

# Start CloudWatch agent
echo "Starting CloudWatch agent..."
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config \
  -m ec2 \
  -s \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json

echo "User data script completed successfully!"
