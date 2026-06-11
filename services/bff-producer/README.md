# BFF Producer Server

## Intro

This is an implementation of the backend server for producer frontend. 

## Basics

### Framework
It is setup as a spring-boot service with resource providers (HAPI FHIR) to server the resources as they will be usefull in the context of the immunization.

### OpenAPI/Swagger-UI
There is an openapi/swagger-ui added to have a simple test possibility

### Security
There is no security layer implemented. So everybody can access the API.

## Usage

You can start it with maven 

	./mvnw spring-boot:run

or directly with java 

	java -jar bff-producer-server-1.0.0-SNAPSHOT.jar
	
or use it as Docker Container
	
	see Docker/readme.md
	

	