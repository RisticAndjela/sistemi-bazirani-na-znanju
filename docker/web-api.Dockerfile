FROM maven:3.9.9-eclipse-temurin-11

WORKDIR /app

COPY backend ./backend
COPY frontend ./frontend

RUN mvn -q -f backend/pom.xml -DskipTests clean install
RUN mvn -q -f frontend/pom.xml -DskipTests compile

EXPOSE 8080

CMD ["mvn", "-q", "-f", "frontend/pom.xml", "exec:java", "-Dexec.mainClass=com.sbnz.frontend.WebApiApp", "-Dsbnz.web.port=8080"]
