import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as cr from 'aws-cdk-lib/custom-resources';
import { Construct } from 'constructs';
import * as path from 'path';

export class WebSocketChatSqsCleanupConstruct extends Construct {
  public readonly cleanupResource: cdk.CustomResource;
  public readonly cleanupFunction: lambda.Function;
  public readonly provider: cr.Provider;

  constructor(scope: Construct, id: string) {
    super(scope, id);

    // Lambda function to cleanup SQS queues on stack deletion
    // Assumes CDK commands are executed from cdk/ directory
    this.cleanupFunction = new lambda.Function(this, 'WebSocketChatSqsCleanupFunction', {
      runtime: lambda.Runtime.PYTHON_3_11,
      handler: 'sqs_cleanup_function.handler',
      code: lambda.Code.fromAsset(path.join(process.cwd(), 'resources/lambda')),
      timeout: cdk.Duration.minutes(5),
      description: 'Cleanup dynamically created SQS queues on stack deletion',
    });

    // Grant permissions to list and delete SQS queues
    this.cleanupFunction.addToRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: [
        'sqs:ListQueues',
        'sqs:DeleteQueue',
        'sqs:ListQueueTags',
        'sqs:GetQueueUrl',
      ],
      resources: ['*'],
    }));

    // Create custom resource provider
    this.provider = new cr.Provider(this, 'WebSocketChatSqsCleanupProvider', {
      onEventHandler: this.cleanupFunction,
    });

    // Create custom resource that triggers cleanup on stack deletion
    this.cleanupResource = new cdk.CustomResource(this, 'WebSocketChatSqsCleanupResource', {
      serviceToken: this.provider.serviceToken,
    });
  }
}
