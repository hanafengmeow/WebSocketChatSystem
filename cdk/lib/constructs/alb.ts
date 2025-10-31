import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface AlbConstructProps {
  vpc: ec2.IVpc;
  securityGroup: ec2.ISecurityGroup;
}

export class AlbConstruct extends Construct {
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly targetGroup: elbv2.ApplicationTargetGroup;

  constructor(scope: Construct, id: string, props: AlbConstructProps) {
    super(scope, id);

    // Create Application Load Balancer
    this.alb = new elbv2.ApplicationLoadBalancer(this, 'WebSocketChatALB', {
      vpc: props.vpc,
      internetFacing: true,
      securityGroup: props.securityGroup,
      idleTimeout: cdk.Duration.seconds(3600), // 1 hour for WebSocket connections
    });

    // Create target group for WebSocket traffic on port 8080
    this.targetGroup = new elbv2.ApplicationTargetGroup(this, 'WebSocketChatTargetGroup', {
      vpc: props.vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.INSTANCE,
      healthCheck: {
        enabled: true,
        path: '/health',
        port: '8081',
        protocol: elbv2.Protocol.HTTP,
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
      stickinessCookieDuration: cdk.Duration.days(1),
      deregistrationDelay: cdk.Duration.seconds(30), // Graceful shutdown time
    });

    // Add listener for WebSocket traffic (HTTP on port 80)
    this.alb.addListener('WebSocketChatListener', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      defaultTargetGroups: [this.targetGroup],
    });
  }
}
