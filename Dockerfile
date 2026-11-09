# Image of the application. The jar is built beforehand by the pipeline ("mvnw verify"),
# so the image does not rebuild the project nor download Maven dependencies again.
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="autoloc-api" \
      org.opencontainers.image.source="https://github.com/DAJBINARY/autoloc-api"

WORKDIR /app

# The application never runs as root
RUN addgroup -S autoloc && adduser -S autoloc -G autoloc

ARG JAR_FILE=target/autoloc-api-*.jar
COPY --chown=autoloc:autoloc ${JAR_FILE} app.jar

USER autoloc
EXPOSE 8080

# The database is given at run time, e.g.:
#   docker run -e SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/autoloc_db -e DB_PASSWORD=... <image>
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
