import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as path from 'path';
import * as fs from 'fs';
import * as ejs from 'ejs';
import { Construct } from 'constructs';

export interface EC2InstanceConstructProps {
  vpc: ec2.Vpc;
  securityGroup: ec2.SecurityGroup;
  role: iam.Role;
  appBucket: s3.Bucket;
  jarFileName: string;
}

export class EC2InstanceConstruct extends Construct {
  public readonly instance: ec2.Instance;

  constructor(scope: Construct, id: string, props: EC2InstanceConstructProps) {
    super(scope, id);

    const { vpc, securityGroup, role, appBucket, jarFileName } = props;

    // Load user data script from file using EJS
    // Assumes CDK commands are executed from cdk/ directory
    const userData = ec2.UserData.forLinux();
    const scriptPath = path.join(process.cwd(), 'resources/ec2/consumer/userdata.sh');

    if (!fs.existsSync(scriptPath)) {
      throw new Error(`User data script not found: ${scriptPath}`);
    }

    const scriptTemplate = fs.readFileSync(scriptPath, 'utf8');
    const userDataScript = ejs.render(scriptTemplate, {
      BUCKET_NAME: appBucket.bucketName,
      JAR_FILE: jarFileName
    });

    userData.addCommands(userDataScript);

    // Create EC2 instance for consumer
    this.instance = new ec2.Instance(this, 'WebSocketChatConsumer', {
      vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PUBLIC,
      },
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      securityGroup,
      role,
      userData,
      keyPair: ec2.KeyPair.fromKeyPairName(this, 'WebSocketChatKeyPair', 'Chuhans-MacBook-Pro-M1.local'),
    });
  }
}
