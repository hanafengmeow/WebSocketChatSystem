import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';

export interface EC2RoleConstructProps {
  appBucket: s3.Bucket;
  serviceRegistryTable: dynamodb.Table;
}

export class EC2RoleConstruct extends Construct {
  public readonly role: iam.Role;

  constructor(scope: Construct, id: string, props: EC2RoleConstructProps) {
    super(scope, id);

    this.role = new iam.Role(this, 'WebSocketChatEC2Role', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
        iam.ManagedPolicy.fromAwsManagedPolicyName('CloudWatchAgentServerPolicy'),
      ],
    });

    // Grant read access to S3 bucket
    props.appBucket.grantRead(this.role);

    // Grant permissions to publish CloudWatch metrics
    this.role.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'cloudwatch:PutMetricData',
      ],
      resources: ['*'],
    }));

    // Grant permissions to publish logs to CloudWatch Logs
    this.role.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'logs:CreateLogGroup',
        'logs:CreateLogStream',
        'logs:PutLogEvents',
        'logs:DescribeLogStreams',
      ],
      resources: [
        'arn:aws:logs:*:*:log-group:websocketchat-server:*',
        'arn:aws:logs:*:*:log-group:websocketchat-consumer:*',
      ],
    }));

    // Grant permissions to manage SQS queues
    this.role.addToPolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'sqs:SendMessage',
        'sqs:ReceiveMessage',
        'sqs:DeleteMessage',
        'sqs:GetQueueUrl',
        'sqs:CreateQueue',
        'sqs:TagQueue',
        'sqs:GetQueueAttributes',
      ],
      resources: ['arn:aws:sqs:*:*:chat-room-*'],
    }));

    // Grant read/write access to DynamoDB service registry
    props.serviceRegistryTable.grantReadWriteData(this.role);
  }
}
