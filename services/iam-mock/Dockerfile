FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py .
RUN  mkdir -p data
COPY app/data/users.json ./data/users.json
EXPOSE 8080
CMD ["python", "server.py"]
