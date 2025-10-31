import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';

export class ServiceRegistryConstruct extends Construct {
  public readonly table: dynamodb.Table;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    // Create DynamoDB table for consumer registry
    this.table = new dynamodb.Table(this, 'WebSocketChatConsumerRegistry', {
      tableName: 'WebSocketChatConsumerRegistry',
      partitionKey: {
        name: 'roomId',
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: 'consumerId',
        type: dynamodb.AttributeType.STRING,
      },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      timeToLiveAttribute: 'expiresAt',
    });

    // Output table name
    new cdk.CfnOutput(this, 'ConsumerRegistryTableName', {
      value: this.table.tableName,
      description: 'DynamoDB Consumer Registry Table Name',
    });
  }
}
