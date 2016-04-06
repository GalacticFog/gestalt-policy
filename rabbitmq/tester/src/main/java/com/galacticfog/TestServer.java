package com.galacticfog;
import java.io.IOException;
import java.util.Random;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

public class TestServer {

	private static final String TASK_QUEUE_NAME = "task_queue";
	private static final String EXCHANGE_NAME = "test-exchange";
	private static final String ROUTE_KEY = "policy";

	public static void main(String[] argv)
		throws java.lang.Exception {

			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost("192.168.200.20");
			factory.setPort(10000);
			Connection connection = factory.newConnection();
			Channel channel = connection.createChannel();
			channel.exchangeDeclare( EXCHANGE_NAME, "direct" );
			//channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
			//channel.queueBind( TASK_QUEUE_NAME, EXCHANGE_NAME, ROUTE_KEY );

			//String message = getMessage(argv);
			int MAX_MSGS = 20;
			for( int i = 0; i < MAX_MSGS; ++i )
			{
				String message = "{ \"name\" : \"Event_" + i + "\", \"data\" : { \"test\" : \"data\" } }";

				channel.basicPublish( EXCHANGE_NAME, ROUTE_KEY, null, message.getBytes());
				System.out.println(" [x] Sent '" + message + "'");
			}

			channel.close();
			connection.close();
		}      

	private static String getMessage(String[] strings){
		if (strings.length < 1)
			return "Hello World!";
		return joinStrings(strings, " ");
	}

	private static String joinStrings(String[] strings, String delimiter) {
		int length = strings.length;
		if (length == 0) return "";
		StringBuilder words = new StringBuilder(strings[0]);
		for (int i = 1; i < length; i++) {
			words.append(delimiter).append(strings[i]);
		}
		return words.toString();
	}
}
