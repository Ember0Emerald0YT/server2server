# server2server
Server to server communication with UndertowClient in HTTP 2.0

Server 1 is listening to 7001 http and 7443 https.

Server 2 is listening to 8001 http and 8443 https. 

For this test, we should use https port all the time and the http port is used for debugging only.

Server1 provides /get and this endpoint will call server2 /get with UndertowClient with HTTP 2.0

Server2 provides /post and this endpoint will call server2 /post with UndertowClient with HTTP 2.0

To test it under load 

Build and start server 2

```
cd server2server/server2
mvn clean install
java -jar target/hello-undertow.jar
```

Build and start server 1

```
cd server2server/server1
mvn clean install
java -jar target/hello-undertow.jar
```

Now you can use curl to access server 1 get endpoint


```
curl -k https://localhost:7443/get
```

You can use curl to access server 1 post endpoint


```

```

To generate enough load with wrk get endpoint

```
```

To generate enough load with wrk post endpoint

```
```
