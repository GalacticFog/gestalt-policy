package com.galacticfog;
import java.io.IOException;
import java.util.Random;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

public class TestServer {

	private static final String TASK_QUEUE_NAME = "lambda-queue";
	private static final String EXCHANGE_NAME = "lambda-executor-exchange";
	private static final String ROUTE_KEY = "lambda-input";

	public static void main(String[] argv)
		throws java.lang.Exception {

			//String payload = "{ \"code\": \"ZnVuY3Rpb24gaGVsbG8oZXZlbnQsIGNvbnRleHQpIHsNCiAgLy8gQ2FsbCB0aGUgY29uc29sZS5sb2cgZnVuY3Rpb24uDQogIGNvbnNvbGUubG9nKCJIZWxsbyBXb3JsZCIpOw0KICB2YXIgZW52ID0gamF2YS5sYW5nLlN5c3RlbS5nZXRlbnYoKTsNCiAgdmFyIHRlc3QgPSBlbnYuZ2V0KCJNRVRBX1RFU1QiKTsNCiAgcmV0dXJuICI8aHRtbD48aGVhZD48L2hlYWQ+PGJvZHk+PGgxPjxjZW50ZXI+SEVMTE8gV09STEQgSU5MSU5FIENPREUhISAtICIgKyB0ZXN0ICsgIiA8aHI+PC9oMT48YnI+PGg0PlNlcnZlcmxlc3Mgd2VicGFnZSEhITwvaDQ+PGJyPjxibGluaz53MDB0PC9ibGluaz48L2NlbnRlcj48L2JvZHk+PC9odG1sPiI7DQp9Ow==\", \"description\": \"super simple twilio call lambda\", \"functionName\": \"hello\", \"handler\": \"hello.js\", \"memorySize\": 511, \"cpus\": 0.2, \"publish\": false, \"role\": \"arn:aws:iam::245814043176:role/GFILambda\", \"runtime\": \"nodejs\", \"timeoutSecs\": 180, \"headers\" : [ { \"key\" : \"Accept\", \"value\" : \"text/html\" } ] }";
			//String payload = "{ \"artifactUri\": \"https://s3.amazonaws.com/gfi.lambdas/hello_world.zip\", \"description\": \"super simple hellow world lambda\", \"functionName\": \"hello\", \"handler\": \"hello_world.js\", \"memorySize\": 1024, \"cpus\": 0.2, \"publish\": false, \"role\": \"arn:aws:iam::245814043176:role/GFILambda\", \"runtime\": \"nodejs\", \"timeoutSecs\": 180, \"headers\" : [ { \"key\" : \"Accept\", \"value\" : \"text/html\" } ] }";
			String payload = "{ \"lambda\": { \"lambdaId\" : \"d3e281e7-9b65-4cbc-b19e-32bc3d22f8eb\", \"artifactUri\": \"https://s3.amazonaws.com/gfi.lambdas/hello_world.zip\", \"description\": \"super simple hellow world lambda\", \"functionName\": \"hello\", \"handler\": \"hello_world.js\", \"memorySize\": 1024, \"cpus\": 0.2, \"publish\": false, \"role\": \"arn:aws:iam::245814043176:role/GFILambda\", \"runtime\": \"nodejs\", \"timeoutSecs\": 180, \"headers\": [ { \"key\": \"Accept\", \"value\": \"text/html\" } ] }, \"data\": \"{}\", \"uuid\": \"betterBeUnique\" }";
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost("192.168.65.111");
			factory.setPort(28353);
			Connection connection = factory.newConnection();
			Channel channel = connection.createChannel();
			channel.exchangeDeclare( EXCHANGE_NAME, "direct" );
			//channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);
			//channel.queueBind( TASK_QUEUE_NAME, EXCHANGE_NAME, ROUTE_KEY );

			//String message = getMessage(argv);
			int MAX_MSGS = 2;
			for( int i = 0; i < MAX_MSGS; ++i )
			{
				/*
				String hello = "Hello World";
				Random rnd = new Random();
				int num = rnd.nextInt( 10 );
				String dots = new String( new char[num] ).replace( "\0", "." );

				String message = hello + Integer.toString( i ) + dots;
				*/

				channel.basicPublish( EXCHANGE_NAME, ROUTE_KEY, null, payload.getBytes() );
				System.out.println(" [x] Sent message " + i );
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
