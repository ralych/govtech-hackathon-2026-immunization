import base64
import json
import time
import jwt
from flask import Flask, request, jsonify

app = Flask(__name__)

JWT_SECRET = "iam-mock-secret"
JWT_ALGO = "HS256"
JWT_TTL = 3600

USERS = {
    "patient1": {"password": "pass123", "user_id": "10001", "role": "patient"},
    "patient2": {"password": "pass123", "user_id": "10002", "role": "patient"},
    "patient3": {"password": "pass123", "user_id": "10003", "role": "patient"},
    "patient4": {"password": "pass123", "user_id": "10004", "role": "patient"},
    "doctor1": {"password": "pass123", "user_id": "20001", "role": "doctor"},
}


def parse_basic_auth(auth_header):
    if not auth_header or not auth_header.startswith("Basic "):
        return None, None
    try:
        decoded = base64.b64decode(auth_header[6:]).decode("utf-8")
        username, password = decoded.split(":", 1)
        return username, password
    except Exception:
        return None, None


@app.route("/authenticate", methods=["POST"])
def authenticate():
    username, password = parse_basic_auth(request.headers.get("Authorization"))
    if not username or username not in USERS:
        return jsonify({"error": "Invalid credentials"}), 401

    user = USERS[username]
    if password != user["password"]:
        return jsonify({"error": "Invalid credentials"}), 401

    now = int(time.time())
    token = jwt.encode(
        {
            "sub": user["user_id"],
            "role": user["role"],
            "iat": now,
            "exp": now + JWT_TTL,
        },
        JWT_SECRET,
        algorithm=JWT_ALGO,
    )

    return jsonify({"token": token, "userId": user["user_id"], "role": user["role"]})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)
