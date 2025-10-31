import boto3
import json


def handler(event, context):
    """
    Lambda function to cleanup dynamically created SQS queues.
    Triggered on CloudFormation stack deletion.
    Deletes all chat-room-* queues tagged with ManagedBy=WebSocketChatSystem.
    """
    if event['RequestType'] == 'Delete':
        sqs = boto3.client('sqs')

        # List all queues with chat-room- prefix
        response = sqs.list_queues(QueueNamePrefix='chat-room-')
        queue_urls = response.get('QueueUrls', [])

        deleted_count = 0
        for queue_url in queue_urls:
            try:
                # Check if queue has our management tag
                tags_response = sqs.list_queue_tags(QueueUrl=queue_url)
                tags = tags_response.get('Tags', {})

                if tags.get('ManagedBy') == 'WebSocketChatSystem':
                    print(f'Deleting queue: {queue_url}')
                    sqs.delete_queue(QueueUrl=queue_url)
                    deleted_count += 1
            except Exception as e:
                print(f'Error deleting queue {queue_url}: {str(e)}')

        print(f'Cleanup completed. Deleted {deleted_count} queues.')
        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'Cleanup completed',
                'deletedQueues': deleted_count
            })
        }

    return {
        'statusCode': 200,
        'body': json.dumps({'message': 'No action needed'})
    }
