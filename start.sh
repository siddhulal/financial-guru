#!/bin/bash
# FinancialGuru — Start all services

set -e

echo "🚀 Starting FinancialGuru..."
echo ""

# Check dependencies
if ! command -v docker &>/dev/null; then
  echo "❌ Docker not found. Install: brew install docker"
  exit 1
fi
if ! command -v ollama &>/dev/null; then
  echo "❌ Ollama not found. Install: brew install ollama"
  exit 1
fi

# Step 1: PostgreSQL
echo "📦 Starting PostgreSQL..."
docker compose up -d postgres
echo "   Waiting for Postgres to be ready..."
sleep 3
echo "   ✅ PostgreSQL running on :5432"
echo ""

# Step 2: Ollama
echo "🤖 Starting Ollama..."
if ! pgrep -x "ollama" > /dev/null; then
  ollama serve &>/dev/null &
  sleep 2
fi
echo "   Checking for llama3.1:13b..."
if ollama list | grep -q "gemma3:4b"; then
  echo "   Using gemma3:4b ✅"
elif ollama list | grep -q "qwen2.5-coder:7b"; then
  echo "   Using qwen2.5-coder:7b ✅"
  sed -i '' 's/OLLAMA_MODEL=gemma3:4b/OLLAMA_MODEL=qwen2.5-coder:7b/' ../.env 2>/dev/null || true
else
  echo "   No suitable model found. Pulling gemma3:4b (~3GB)..."
  ollama pull gemma3:4b
fi
echo "   ✅ Ollama running on :11434"
echo ""

# Step 3: Backend
echo "☕ Starting Spring Boot backend..."
cd backend
./mvnw spring-boot:run -q &
BACKEND_PID=$!
echo "   PID: $BACKEND_PID"
echo "   Waiting for backend to start..."
until curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; do
  sleep 2
done
echo "   ✅ Backend running on :8080"
echo "   📖 Swagger: http://localhost:8080/swagger-ui"
cd ..
echo ""

# Step 4: Frontend
echo "⚡ Starting Next.js frontend..."
cd frontend
if [ ! -d "node_modules" ]; then
  echo "   Installing dependencies..."
  npm install
fi
npm run dev &
FRONTEND_PID=$!
echo "   PID: $FRONTEND_PID"
echo "   ✅ Frontend starting on :3000"
cd ..
echo ""

echo "╔══════════════════════════════════════╗"
echo "║     FinancialGuru is running! 🎉      ║"
echo "╠══════════════════════════════════════╣"
echo "║  Dashboard:  http://localhost:3001    ║"
echo "║  API Docs:   http://localhost:8080    ║"
echo "║  pgAdmin:    http://localhost:5050    ║"
echo "║  Ollama:     http://localhost:11434   ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo "Press Ctrl+C to stop all services"

# Wait for Ctrl+C
trap "echo ''; echo 'Stopping...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; docker compose stop; exit 0" INT
wait
