# server2server
Server to server communication with UndertowClient in HTTP 2.0

Server 1 is listening to 7001 http and 7443 https.

Server 2 is listening to 8001 http and 8443 https. 

For this test, we should use https port all the time and the http port is used for debugging only.

Server1 provides /get and this endpoint will call server2 /get with UndertowClient

Server2 provides /post and this endpoint will call server2 /post with UndertowClient

To reproduce the issue

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

And here is the error message on server1

```
java.nio.channels.ClosedChannelException
	at io.undertow.client.http2.Http2ClientConnection.sendRequest(Http2ClientConnection.java:132)
	at hello.Server1.lambda$getHandler$0(Server1.java:83)
	at io.undertow.server.RoutingHandler.handleRequest(RoutingHandler.java:93)
	at io.undertow.server.Connectors.executeRootHandler(Connectors.java:332)
	at io.undertow.server.protocol.http.HttpReadListener.handleEventWithNoRunningRequest(HttpReadListener.java:254)
	at io.undertow.server.protocol.http.HttpReadListener.handleEvent(HttpReadListener.java:136)
	at io.undertow.server.protocol.http.HttpReadListener.handleEvent(HttpReadListener.java:59)
	at org.xnio.ChannelListeners.invokeChannelListener(ChannelListeners.java:92)
	at org.xnio.conduits.ReadReadyHandler$ChannelListenerHandler.readReady(ReadReadyHandler.java:66)
	at io.undertow.protocols.ssl.SslConduit$SslReadReadyHandler.readReady(SslConduit.java:1140)
	at org.xnio.nio.NioSocketConduit.handleReady(NioSocketConduit.java:88)
	at org.xnio.nio.WorkerThread.run(WorkerThread.java:561)
java.lang.NullPointerException
	at hello.Server1.lambda$getHandler$0(Server1.java:85)
	at io.undertow.server.RoutingHandler.handleRequest(RoutingHandler.java:93)
	at io.undertow.server.Connectors.executeRootHandler(Connectors.java:332)
	at io.undertow.server.protocol.http.HttpReadListener.handleEventWithNoRunningRequest(HttpReadListener.java:254)
	at io.undertow.server.protocol.http.HttpReadListener.handleEvent(HttpReadListener.java:136)
	at io.undertow.server.protocol.http.HttpReadListener.handleEvent(HttpReadListener.java:59)
	at org.xnio.ChannelListeners.invokeChannelListener(ChannelListeners.java:92)
	at org.xnio.conduits.ReadReadyHandler$ChannelListenerHandler.readReady(ReadReadyHandler.java:66)
	at io.undertow.protocols.ssl.SslConduit$SslReadReadyHandler.readReady(SslConduit.java:1140)
	at org.xnio.nio.NioSocketConduit.handleReady(NioSocketConduit.java:88)
	at org.xnio.nio.WorkerThread.run(WorkerThread.java:561)

```

If we change the url to https from h2c-prior, it will work but not stable under load. 

