import * as cdk from 'aws-cdk-lib';
import * as path from 'path';
import * as fs from 'fs';
import { Construct } from 'constructs';
import { VpcConstruct } from './constructs/vpc';
import { S3DeploymentConstruct } from './constructs/s3-deployment';
import { SecurityGroupConstruct } from './constructs/security-group';
import { EC2RoleConstruct } from './constructs/iam-role';
import { AsgConstruct } from './constructs/asg';
import { EC2InstanceConstruct } from './constructs/ec2-instance';
import { WebSocketChatSqsCleanupConstruct } from './constructs/sqs-cleanup';
import { AlbConstruct } from './constructs/alb';
import { ServiceRegistryConstruct } from './constructs/service-registry';

export class ChatSystemStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Determine JAR filename from build.gradle
    const buildGradlePath = path.join(__dirname, '../../build.gradle');
    const buildGradleContent = fs.readFileSync(buildGradlePath, 'utf8');
    const versionMatch = buildGradleContent.match(/version\s*=\s*['"](.+?)['"]/);
    if (!versionMatch) {
      throw new Error('Could not find version in build.gradle');
    }
    const version = versionMatch[1];
    const jarFileName = `WebSocketChatSystemPOC-${version}.jar`;

    // Create VPC
    const vpcConstruct = new VpcConstruct(this, 'WebSocketChatVPC');

    // Create S3 bucket and deploy application
    const s3DeploymentConstruct = new S3DeploymentConstruct(this, 'WebSocketChatS3Deployment', {
      jarFileName,
    });

    // Create security groups
    const securityGroupConstruct = new SecurityGroupConstruct(this, 'WebSocketChatSecurityGroup', {
      vpc: vpcConstruct.vpc,
    });

    // Create DynamoDB service registry
    const serviceRegistryConstruct = new ServiceRegistryConstruct(this, 'WebSocketChatServiceRegistry');

    // Create IAM role
    const ec2RoleConstruct = new EC2RoleConstruct(this, 'WebSocketChatEC2Role', {
      appBucket: s3DeploymentConstruct.bucket,
      serviceRegistryTable: serviceRegistryConstruct.table,
    });

    // Create consumer EC2 instance (internal microservice)
    const consumerInstance = new EC2InstanceConstruct(this, 'WebSocketChatConsumer', {
      vpc: vpcConstruct.vpc,
      securityGroup: securityGroupConstruct.consumerSecurityGroup,
      role: ec2RoleConstruct.role,
      appBucket: s3DeploymentConstruct.bucket,
      jarFileName,
    });

    // Make sure all bucket deployments complete before consumer starts
    consumerInstance.instance.node.addDependency(s3DeploymentConstruct.jarDeployment);
    consumerInstance.instance.node.addDependency(s3DeploymentConstruct.consumerConfigDeployment);

    // Create ALB
    const albConstruct = new AlbConstruct(this, 'WebSocketChatAlb', {
      vpc: vpcConstruct.vpc,
      securityGroup: securityGroupConstruct.albSecurityGroup,
    });

    // Create Auto Scaling Group
    const asgConstruct = new AsgConstruct(this, 'WebSocketChatServer', {
      vpc: vpcConstruct.vpc,
      securityGroup: securityGroupConstruct.serverSecurityGroup,
      role: ec2RoleConstruct.role,
      appBucket: s3DeploymentConstruct.bucket,
      jarFileName,
    });

    // Attach ASG to ALB target group
    asgConstruct.asg.attachToApplicationTargetGroup(albConstruct.targetGroup);

    // Make sure server deployments and consumer complete before the ASG starts
    asgConstruct.asg.node.addDependency(s3DeploymentConstruct.jarDeployment);
    asgConstruct.asg.node.addDependency(s3DeploymentConstruct.serverConfigDeployment);
    asgConstruct.asg.node.addDependency(consumerInstance.instance);

    // Output the ALB DNS name
    new cdk.CfnOutput(this, 'LoadBalancerDNS', {
      value: albConstruct.alb.loadBalancerDnsName,
      description: 'DNS name of the Application Load Balancer',
    });

    new cdk.CfnOutput(this, 'WebSocketURL', {
      value: `ws://${albConstruct.alb.loadBalancerDnsName}/chat/{roomid}`,
      description: 'WebSocket endpoint URL',
    });

    // Create SQS cleanup resource for stack deletion
    const sqsCleanup = new WebSocketChatSqsCleanupConstruct(this, 'WebSocketChatSqsCleanup');

    // Ensure ASG is deleted BEFORE the cleanup Lambda and provider
    // This way: ASG deleted -> cleanup runs -> Lambda/Provider deleted
    asgConstruct.asg.node.addDependency(sqsCleanup.cleanupFunction);
    asgConstruct.asg.node.addDependency(sqsCleanup.provider);
  }
}
