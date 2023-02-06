FROM openjdk:11

ARG TIMEZONE="Asia/Seoul"

ARG DEBIAN_FROMTEND=noninteracive
ENV TZ=Asia/Seoul
RUN apt-get install -y tzdata
RUN cp /usr/share/zoneinfo/${TIMEZONE} /etc/localtime

RUN useradd -ms /bin/bash -u 1001 byung8

RUN mkdir -p /webhook/run
RUN mkdir -p /webhook/logs
RUN mkdir -p /webhook/files

COPY files/* /webhook/files/
COPY target/webhook-0.0.1-SNAPSHOT.jar /webhook/app.jar
RUN chown -R byung8:byung8 /webhook
USER byung8

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","-Dspring.profiles.active=prod","-Duser.timezone=Asia/Seoul","/webhook/app.jar"]
