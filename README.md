
# apim-proxy

Proxy for the APIM's Platform Management API Service. Serves as the Platform Management API in a
non-prod environment. Forwards calls to the real Platform Management API via HODS Proxy
and Scrubbing Centre.

In environments without a real Platform Management API then this proxy forwards to the Platform Management API stub,
[api-hub-apim-stubs](https://github.com/hmrc/api-hub-apim-stubs).

## Requirements

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## Dependencies
Beyond the typical HMRC Digital platform dependencies this service relies on:
- APIM's Platform Management API service

The full set of dependencies can be started using Service Manager and the group API_HUB_ALL.

You can view service dependencies using the Tax Catalogue's Service Relationships
section here:
https://catalogue.tax.service.gov.uk/service/apim-proxy

### Platform Management API
All requests to this service starting with are forwarded to the Platform Management API in APIM.
The incoming `Authorization` header is passed on. This
service does not add authorisation.

The Platform Management API to use is configured in `application.conf` in these settings:
- `microservice.services.platform-management-api`

## Using the service

### Running the application

To run the application use `sbt run` to start the service. All local dependencies should be running first.

Once everything is up and running you can access the application at

```
http://localhost:9000/apim-proxy
```

### Authentication
This service does not authenticate incoming requests.

## Building the service
This service can be built on the command line using sbt.
```
sbt compile
```

### Unit tests
This microservice has many unit tests that can be run from the command line:
```
sbt test
```

### Integration tests
This microservice has some integration tests that can be run from the command line:
```
sbt it:test
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").