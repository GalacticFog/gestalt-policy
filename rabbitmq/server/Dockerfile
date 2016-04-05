FROM java:latest
ADD target/worker-test-1.0-SNAPSHOT-fat.jar worker.jar
ENTRYPOINT [ "java", "-jar", "worker.jar"]
