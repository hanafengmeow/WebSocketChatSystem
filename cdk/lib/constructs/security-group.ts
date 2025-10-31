import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';

export interface SecurityGroupConstructProps {
  vpc: ec2.Vpc;
}

export class SecurityGroupConstruct extends Construct {
  public readonly albSecurityGroup: ec2.SecurityGroup;
  public readonly serverSecurityGroup: ec2.SecurityGroup;
  public readonly consumerSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: SecurityGroupConstructProps) {
    super(scope, id);

    // Security group for ALB
    this.albSecurityGroup = new ec2.SecurityGroup(this, 'WebSocketChatAlbSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for Application Load Balancer',
      allowAllOutbound: true,
    });

    // Allow HTTP traffic from internet to ALB
    this.albSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'Allow HTTP traffic from internet'
    );

    // Security group for Server EC2 instances
    this.serverSecurityGroup = new ec2.SecurityGroup(this, 'WebSocketChatServerSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for WebSocket Chat Server EC2 instances',
      allowAllOutbound: true,
    });

    // Allow HTTP traffic from ALB to server on port 8080
    this.serverSecurityGroup.addIngressRule(
      this.albSecurityGroup,
      ec2.Port.tcp(8080),
      'Allow HTTP traffic from ALB on port 8080'
    );

    // Allow health check traffic from ALB to server on port 8081
    this.serverSecurityGroup.addIngressRule(
      this.albSecurityGroup,
      ec2.Port.tcp(8081),
      'Allow health check traffic from ALB on port 8081'
    );

    // Allow SSH for management
    this.serverSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(22),
      'Allow SSH access to server'
    );

    // Security group for Consumer EC2 instances
    this.consumerSecurityGroup = new ec2.SecurityGroup(this, 'WebSocketChatConsumerSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for WebSocket Chat Consumer EC2 instances',
      allowAllOutbound: true,
    });

    // Allow WebSocket/STOMP traffic from server to consumer on port 9090
    this.consumerSecurityGroup.addIngressRule(
      this.serverSecurityGroup,
      ec2.Port.tcp(9090),
      'Allow STOMP WebSocket traffic from server on port 9090'
    );

    // Allow health check traffic from server to consumer on port 9091
    this.consumerSecurityGroup.addIngressRule(
      this.serverSecurityGroup,
      ec2.Port.tcp(9091),
      'Allow health check traffic from server on port 9091'
    );

    // Allow SSH for management
    this.consumerSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(22),
      'Allow SSH access to consumer'
    );
  }
}
