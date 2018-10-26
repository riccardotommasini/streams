FROM openjdk:8

COPY . /usr/src/colorwave
WORKDIR /usr/src/colorwave

ADD target/colorwave-1.0.jar ./colorwave.jar
ADD start.sh ./start.sh

RUN chmod u+x start.sh

EXPOSE 1255 2255 3255 2553 2552 2551

ENTRYPOINT ["./start.sh"]
