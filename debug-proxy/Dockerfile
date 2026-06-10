FROM python:3.11-slim
WORKDIR /app
RUN pip install --no-cache-dir paho-mqtt
COPY proxy.py tcp_probe.py mqtt_subscriber.py ./
ENV BACKEND=kapua-api:8080
ENV PROXY_PORT=7080
ENV UI_PORT=9999
CMD ["python", "-u", "proxy.py"]
