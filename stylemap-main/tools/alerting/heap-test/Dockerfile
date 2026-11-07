# Reuse the existing pinned local StyleMap JRE image; replace its executable JAR.
# The fixture JAR has its own configuration and no database/security/mail dependencies.
FROM stylemap-backend:k8s-v1
COPY target/stylemap-heap-test.jar /app/app.jar
USER 65534:65534
EXPOSE 19081 19082
ENTRYPOINT ["java", "-Xms128m", "-Xmx128m", "-XX:+UseSerialGC", "-XX:NewRatio=9", "-jar", "/app/app.jar"]
