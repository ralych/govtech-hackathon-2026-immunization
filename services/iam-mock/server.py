import base64
import json
import os
import time
import jwt
from flask import Flask, request, jsonify

app = Flask(__name__)

JWT_SECRET = "iam-mock-secret"
JWT_ALGO = "HS256"
JWT_TTL = 3600
USERS_FILE = os.environ.get(
    "IAM_MOCK_USERS_FILE",
    os.path.join("/app/data", "users.json"),
)

def load_users(users_file):
    with open(users_file, "r", encoding="utf-8") as f:
        users = json.load(f)

    if not isinstance(users, dict):
        raise ValueError("Users file must contain a JSON object at the top level")

    for username, user_data in users.items():
        if not isinstance(user_data, dict):
            raise ValueError(f"User '{username}' must be an object")

        missing_fields = {"password", "user_id", "role"} - set(user_data.keys())
        if missing_fields:
            raise ValueError(
                f"User '{username}' is missing required fields: {sorted(missing_fields)}"
            )

    return users


USERS = load_users(USERS_FILE)


@app.route("/users", methods=["GET"])
def users():
    safe_users = []
    for username, user_data in USERS.items():
        safe_users.append(
            {
                "username": username,
                "name": user_data.get("name", username),
                "role": user_data["role"],
            }
        )

    safe_users.sort(key=lambda user: user["username"])
    return jsonify({"users": safe_users})

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
