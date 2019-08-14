# Config Schema Service
***
## Overview
*	Part of project Logging Service 
*	Be a REST Service to manage Requests received.
*	A Producer for send data to Kafka
*	Service will wait for requests to:
		* Send request to Config Schema Service for getting latest schema
		* Receive records from requets, validate and send them to Topic.
*	Use Rapidoid for Server
*	Use Kafka for streaming
*	Use Hadoop for database
***
## Build
mvn package
***
## Run
### Start zookeeper(in kafka folder)
	bin/zookeeper-server-start.sh config/zookeeper.properties

### Start kafka server (in kafka folder)
	bin/kafka-server-start.sh config/server.properties

### Start Schema Registry (in registry folder)
	bin/registry-server-start conf/registry.yaml

### Start Config Schema Service
	See more in: [Config Schema](https://github.com/phamhuyhoang97/configSchema)

### Start web server
	java -jar webLog.jar

### Test send requets to server "localhost:19080" with WRK
	wrk -t6 -c2000 -d1s "http://localhost:19080/abc?a1=a1&a2=a2&a3=a3&a4=a4&a5=a5&a6=a6&a7=7&a8=8&a9=9&a10=10&a11=11&a12=12&a13=13&a14=2019-01-01&a15=2019-01-02&a16=2019-01-03&a17=2019-01-04&a18=2019-01-05&a19=13"

### See more optionals in file 'note.txt'
