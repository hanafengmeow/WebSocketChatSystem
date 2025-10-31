import * as cdk from 'aws-cdk-lib';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import * as path from 'path';
import * as fs from 'fs';
import { Construct } from 'constructs';

export interface S3DeploymentConstructProps {
  jarFileName: string;
}

export class S3DeploymentConstruct extends Construct {
  public readonly bucket: s3.Bucket;
  public readonly jarDeployment: s3deploy.BucketDeployment;
  public readonly serverConfigDeployment: s3deploy.BucketDeployment;
  public readonly consumerConfigDeployment: s3deploy.BucketDeployment;

  constructor(scope: Construct, id: string, props: S3DeploymentConstructProps) {
    super(scope, id);

    // Create S3 bucket for application deployment
    this.bucket = new s3.Bucket(this, 'WebSocketChatAppBucket', {
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    // Validate JAR file exists before deployment
    const jarPath = path.join(process.cwd(), '../build/libs');
    const jarFilePath = path.join(jarPath, props.jarFileName);

    if (!fs.existsSync(jarFilePath)) {
      throw new Error(`JAR file not found: ${jarFilePath}. Run 'gradle build' first.`);
    }

    console.log(`Deploying JAR file: ${props.jarFileName}`);

    // Deploy JAR files to S3 jars/ prefix
    this.jarDeployment = new s3deploy.BucketDeployment(this, 'WebSocketChatDeployJar', {
      sources: [s3deploy.Source.asset(jarPath)],
      destinationBucket: this.bucket,
      destinationKeyPrefix: 'jars/',
      extract: true,
      prune: false,
    });

    // Validate server config directory exists
    const serverConfigPath = path.join(process.cwd(), 'resources/ec2/server');
    if (!fs.existsSync(serverConfigPath)) {
      throw new Error(`Server config directory not found: ${serverConfigPath}`);
    }

    const cloudwatchConfigPath = path.join(serverConfigPath, 'cloudwatch-config.json');
    if (!fs.existsSync(cloudwatchConfigPath)) {
      throw new Error(`CloudWatch config not found: ${cloudwatchConfigPath}`);
    }

    console.log(`Deploying CloudWatch config from: ${serverConfigPath}`);

    // Deploy server CloudWatch config to S3 server/ subpath
    this.serverConfigDeployment = new s3deploy.BucketDeployment(this, 'WebSocketChatDeployServerConfig', {
      sources: [
        s3deploy.Source.asset(serverConfigPath, {
          exclude: ['userdata.sh'],
        })
      ],
      destinationBucket: this.bucket,
      destinationKeyPrefix: 'server/',
      extract: true,
      prune: false,
    });

    // Validate consumer config directory exists
    const consumerConfigPath = path.join(process.cwd(), 'resources/ec2/consumer');
    if (!fs.existsSync(consumerConfigPath)) {
      throw new Error(`Consumer config directory not found: ${consumerConfigPath}`);
    }

    const consumerCloudwatchConfigPath = path.join(consumerConfigPath, 'cloudwatch-config.json');
    if (!fs.existsSync(consumerCloudwatchConfigPath)) {
      throw new Error(`Consumer CloudWatch config not found: ${consumerCloudwatchConfigPath}`);
    }

    console.log(`Deploying consumer CloudWatch config from: ${consumerConfigPath}`);

    // Deploy consumer CloudWatch config to S3 consumer/ subpath
    this.consumerConfigDeployment = new s3deploy.BucketDeployment(this, 'WebSocketChatDeployConsumerConfig', {
      sources: [
        s3deploy.Source.asset(consumerConfigPath, {
          exclude: ['userdata.sh'],
        })
      ],
      destinationBucket: this.bucket,
      destinationKeyPrefix: 'consumer/',
      extract: true,
      prune: false,
    });
  }
}
