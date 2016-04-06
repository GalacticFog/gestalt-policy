package com.galacticfog;
import com.rabbitmq.client.*;

import java.io.IOException;

public class TestClient {
	private static final String TASK_QUEUE_NAME = "task_queue";
	private static final String EXCHANGE_NAME = "test-exchange";
	private static final String ROUTE_KEY = "policy";

	public static void main(String[] argv) throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("192.168.200.20");
		factory.setPort(10000);
		final Connection connection = factory.newConnection();
		final Channel channel = connection.createChannel();

		channel.exchangeDeclare( EXCHANGE_NAME, "direct" );
		channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
		channel.queueBind( TASK_QUEUE_NAME, EXCHANGE_NAME, ROUTE_KEY );
		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		channel.basicQos(1);

		final Consumer consumer = new DefaultConsumer(channel) {
			@Override
				public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
					String message = new String(body, "UTF-8");

					System.out.println(" [x] Received '" + message + "'");
					try {
						doWork(message);
					} finally {
						System.out.println(" [x] Done");
						channel.basicAck(envelope.getDeliveryTag(), false);
					}
				}
		};
		channel.basicConsume(TASK_QUEUE_NAME, false, consumer);
	}

	private static void doWork(String task) {
		for (char ch : task.toCharArray()) {
			if (ch == '.') {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException _ignored) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
}
