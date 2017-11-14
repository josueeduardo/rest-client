# RestClient - [Unirest](https://github.com/Mashape/unirest-java) fork


[Unirest](http://unirest.io) is a set of lightweight HTTP libraries available in multiple languages, built and maintained by [Mashape](https://github.com/Mashape), who also maintain the open-source API Gateway [Kong](https://github.com/Mashape/kong). 



[![License][license-image]][license-url]


## Features

Apart from the features provided by Unirest Java, this fork also provides:

* Bug fixes
* Independent client configuration
* Improved API
* Updated async requests to use Java 8 CompletableFuture
* Lazy response body parsing
* Default JsonMapper using Gson
* Single idle thread monitor for all clients


### Maven


```xml
<dependency>
    <groupId>io.joshworks.unirest</groupId>
    <artifactId>unirest-java</artifactId>
    <version>1.5.0</version>
</dependency>
```

## Basics

Please read the [Unirest Documentation](https://github.com/Mashape/unirest-java) for basic examples on how to use the core api.
This documentation aims to show the additional features and improvements from the original library.


### Creating a new client with defaults
The following example creates a new basic RestClient. At the moment, each client will have its own 
HttpClient sync and async client.

```java

RestClient client = RestClient.newClient().build();

```

### Base url

```java

RestClient client = RestClient.newClient().baseUrl("http://my-api.com/v1").build();
String response = client.get("/some-resource").asString();

```

### Unirest client
Unirest provides the same static methods as the original version. It's ideal for simple usage with default configuration. 

```java

String response = Unirest.get("http://my-api.com/v1").asString();

```

### Async requests with CompletableFuture
When using asynchronous requests you can use Java 8 CompletableFuture to handle the response.
This also gives you the ability to compose multiple requests in a convenient way. 

```java
client.get("http://my-api.com/v1/hello")
        .asStringAsync()
        .thenAccept(resp -> {
            System.out.println(resp.getBody())
         })
         .exceptionally(e -> {
            e.printStackTrace();
            return null;
         });
         
```

### New multipart/form-data and x-www-form-urlencoded API
The new API for form data makes easier to specify the right values for each type of request. When using `.part(...)` a 
`multipart/form-data` request will be sent, `.field(...)` will create `x-www-form-urlencoded` request. This makes the interface cleaner and less error prone.
The content type is also set automatically.

```java
//multipart
client.post("http://my-service.com/fileUpload")
        .part("param3", value)
        .part("file", new File("test.txt"))
        .asJson();

//form-urlencoded
client.post("http://my-service.com/login")
                .field("username", "admin")
                .field("password", "admin123")
                .asJson();

```


### Serialization
Before an `asObject(Class)` or a `.body(Object)` invokation, is necessary to provide a custom implementation of the `ObjectMapper` interface.
This should be done for each client.
By default Gson is used as the default serializer, so there's no need to register any other unless you need custom configuration.
Here's how to configure a new ObjectMapper:

```java
public class XmlMapper implements ObjectMapper {
   
    @Override
    public <T> T readValue(String value, Class<T> valueType) {
        //...
    }

    @Override
    public String writeValue(Object value) {
        //...
    }
}


RestClient client = RestClient.builder().objectMapper(new XmlMapper()).build();

```

# Exiting an application

RestClient starts a background idle thread monitor, which is a daemon thread. 
When exiting the application, you can use the `ClientContainer` to release all the allocated resources, as follows:

```java
//If a client is no longer needed and you want to dispose its resources
client.shutdown();

//When your application is shutting down:
//Closes all client connections and the monitor
ClientContainer.shutdown();

```

[license-url]: https://github.com/josueeduardo/rest-client/blob/master/LICENSE
[license-image]: https://img.shields.io/badge/license-MIT-blue.svg?style=flat
