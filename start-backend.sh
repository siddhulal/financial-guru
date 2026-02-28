#!/bin/bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/financialguru
export SPRING_DATASOURCE_USERNAME=financialguru
export SPRING_DATASOURCE_PASSWORD=financialguru_secret
export APP_OLLAMA_MODEL=gemma3:4b

JAR=/Users/siddhu/Desktop/Code/workspace_llm/payment-project/financial-guru/backend/target/financial-guru-backend-1.0.0-SNAPSHOT.jar
LOG=/tmp/financialguru-backend.log
JAVA=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java

# Kill any existing instance
lsof -ti:8080 | xargs kill -9 2>/dev/null
sleep 1

echo "Starting FinancialGuru backend..." > "$LOG"
"$JAVA" \
  -jar "$JAR" \
  --spring.datasource.url="$SPRING_DATASOURCE_URL" \
  --spring.datasource.username="$SPRING_DATASOURCE_USERNAME" \
  --spring.datasource.password="$SPRING_DATASOURCE_PASSWORD" \
  --app.ollama.model="$APP_OLLAMA_MODEL" \
  >> "$LOG" 2>&1
