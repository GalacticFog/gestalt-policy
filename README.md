#Gestalt-Policy

This is the home of the gestalt-policy service.  This service contains an implementation of a microservice that listens to a configured RabbitMQ exchange for handling policy messages and interacting with the Gestalt Lambda service.  The service maintains a mapping of policies to lambda instances that need to be called.  When the service hears a mapping with the meta context that is registered the associated lambda will be called.  The policies are currentl side effect only. 

Below is a schematic diagram of the way that data flows through the bigger system.

![Schematic](gestalt-policy-diagram.png)
