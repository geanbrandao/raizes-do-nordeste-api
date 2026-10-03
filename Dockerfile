# Estagio 1 - build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .

# O chmod nao e redundante: o git guarda o gradlew como executavel (100755), mas em
# checkout no Windows esse bit costuma se perder, e ai o build morre com
# "permission denied" dentro do container. Uma linha evita a classe inteira.
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# Estagio 2 - execucao
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
