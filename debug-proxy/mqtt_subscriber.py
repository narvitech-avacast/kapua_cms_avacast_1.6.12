#!/usr/bin/env python3
"""
MQTT subscriber: connects to the Kapua broker and forwards all messages
to the debug-proxy UI via the shared record() callback.
"""
import json, os, threading, time
from datetime import datetime, timezone

BROKER_HOST = os.environ.get("MQTT_HOST", "broker")
BROKER_PORT = int(os.environ.get("MQTT_PORT", "1883"))
MQTT_USER   = os.environ.get("MQTT_USER", "kapua-broker")
MQTT_PASS   = os.environ.get("MQTT_PASS", "kapua-password")
SUBSCRIBE   = os.environ.get("MQTT_TOPICS", "#")

_record_fn = None


def _try_decode(payload: bytes) -> str:
    """Try UTF-8 text, then hex dump for binary."""
    try:
        text = payload.decode("utf-8")
        # Pretty-print if it looks like JSON
        try:
            return json.dumps(json.loads(text), ensure_ascii=False, indent=2)
        except Exception:
            return text
    except Exception:
        return payload.hex(" ", 1)


def _on_message(client, userdata, msg):
    if not _record_fn:
        return
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S.%f")[:-3]
    body = _try_decode(msg.payload) if msg.payload else "(empty)"
    entry = {
        "ts"          : ts,
        "method"      : "MQTT",
        "path"        : msg.topic,
        "req_headers" : {
            "Topic"    : msg.topic,
            "QoS"      : str(msg.qos),
            "Retain"   : str(msg.retain),
            "Payload"  : f"{len(msg.payload)} bytes",
        },
        "req_body"    : body,
        "res_status"  : 0,
        "res_headers" : {},
        "res_body"    : "",
        "error"       : None,
        "duration_ms" : 0,
    }
    _record_fn(entry)


def _on_connect(client, userdata, flags, rc):
    codes = {0: "OK", 1: "bad protocol", 2: "bad clientId",
             3: "server unavailable", 4: "bad credentials", 5: "not authorized"}
    print(f"[MQTT] connect rc={rc} ({codes.get(rc,'?')})", flush=True)
    if rc == 0:
        client.subscribe(SUBSCRIBE, qos=0)
        print(f"[MQTT] subscribed to '{SUBSCRIBE}'", flush=True)


def _on_disconnect(client, userdata, rc):
    print(f"[MQTT] disconnected rc={rc}", flush=True)


def start(record_fn):
    global _record_fn
    _record_fn = record_fn

    def _run():
        import paho.mqtt.client as mqtt
        client = mqtt.Client(client_id="debug-proxy-subscriber", clean_session=True)
        client.username_pw_set(MQTT_USER, MQTT_PASS)
        client.on_connect    = _on_connect
        client.on_message    = _on_message
        client.on_disconnect = _on_disconnect

        while True:
            try:
                print(f"[MQTT] connecting to {BROKER_HOST}:{BROKER_PORT} …", flush=True)
                client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
                client.loop_forever()
            except Exception as e:
                print(f"[MQTT] error: {e} – retry in 5s", flush=True)
            time.sleep(5)

    threading.Thread(target=_run, name="mqtt-sub", daemon=True).start()
