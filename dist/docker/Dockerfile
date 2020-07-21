FROM openjdk

WORKDIR /app
COPY rafael-twitter-stream-controller-app/build/libs/rafael-twitter-stream-controller-app.jar rafael-twitter-stream-controller-app.jar
CMD ["java", "-jar", "rafael-twitter-stream-controller-app.jar", "--spring.config.additional-location=file:/config/secrets/twitter-secrets.yml"]