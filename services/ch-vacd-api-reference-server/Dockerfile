FROM bellsoft/liberica-openjdk-alpine:latest
EXPOSE 9111

COPY ./target/ch-vacd-api-reference-server-*.jar /ch-vacd-api-server.jar

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'
RUN apk --purge del

# Create a new user and switch to it
#RUN adduser -D server --disabled-password --gecos '' server
#ENV HOME=/home/server
#RUN mkdir -p /database && chown server:server /database && chmod 777 /database
#RUN mkdir -p /config && chown server:server /config
RUN mkdir -p /database
RUN mkdir -p /config
#RUN chown server:server /

#USER server

ENTRYPOINT java -Xmx3072M -Dspring.config.additional-location=optional:file:/config/application.yaml,optional:file:application.yaml -jar /ch-vacd-api-server.jar
