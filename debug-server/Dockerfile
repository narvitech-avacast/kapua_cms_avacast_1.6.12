FROM python:3.11-slim
WORKDIR /app
COPY server.py .
ENV LOG_FILE=/logs/access.log
ENV PORT=9999
CMD ["python", "-u", "server.py"]
