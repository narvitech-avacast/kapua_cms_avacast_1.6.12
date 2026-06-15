#!/usr/bin/env python3
"""
Grant broker:read permission to the account broker user.
Run once inside the debug-proxy container.
"""
import pymysql
import os

DB_HOST = os.environ.get("DB_HOST", "db")
DB_USER = os.environ.get("DB_USER", "kapua")
DB_PASS = os.environ.get("DB_PASS", "kapua")
DB_NAME = os.environ.get("DB_NAME", "kapua")

TARGET_USER = os.environ.get("TARGET_BROKER_USER", "")

if not TARGET_USER:
    raise SystemExit("TARGET_BROKER_USER is required")

conn = pymysql.connect(host=DB_HOST, port=3306, user=DB_USER, password=DB_PASS,
                       database=DB_NAME, charset="utf8")
cur = conn.cursor()

# Find the broker user
cur.execute("SELECT id, name, scope_id FROM user WHERE name = %s", (TARGET_USER,))
user = cur.fetchone()
if not user:
    print(f"ERROR: user '{TARGET_USER}' not found")
    conn.close()
    exit(1)

user_id, user_name, scope_id = user
print(f"Found user: {user_name}, id={user_id.hex()}, scope_id={scope_id.hex()}")

# Find the AccessInfo for this user
cur.execute("SELECT id FROM access_info WHERE user_id = %s AND scope_id = %s",
            (user_id, scope_id))
access_info = cur.fetchone()
if not access_info:
    print("ERROR: no AccessInfo found for broker user")
    conn.close()
    exit(1)

access_info_id = access_info[0]
print(f"Found AccessInfo id={access_info_id.hex()}")

# Check existing permissions
cur.execute("""
    SELECT ap.id, d.name, ap.action, ap.target_scope_id, ap.forwardable
    FROM access_permission ap
    JOIN domain d ON ap.domain_id = d.id
    WHERE ap.access_info_id = %s
""", (access_info_id,))
existing = cur.fetchall()
print(f"Existing permissions ({len(existing)}):")
for p in existing:
    print(f"  id={p[0].hex()} domain={p[1]} action={p[2]} target_scope={p[3].hex() if p[3] else 'None'}")

# Find broker domain
cur.execute("SELECT id FROM domain WHERE name = 'broker'")
domain = cur.fetchone()
if not domain:
    print("ERROR: 'broker' domain not found")
    conn.close()
    exit(1)
domain_id = domain[0]
print(f"Broker domain id={domain_id.hex()}")

# Check if 'read' (action=2) permission already exists
cur.execute("""
    SELECT id FROM access_permission
    WHERE access_info_id = %s AND domain_id = %s AND action = 2
""", (access_info_id, domain_id))
existing_read = cur.fetchone()
if existing_read:
    print("'read' permission already exists — no action needed")
    conn.close()
    exit(0)

# Insert 'read' permission (action=2 = READ in Kapua)
# Also need to set: id (binary(16)), scope_id, created_on, created_by, modified_on, modified_by,
#                   optlock, access_info_id, domain_id, action, target_scope_id, forwardable

import os as _os
import time, struct

def new_id():
    # Generate a random 16-byte binary ID
    return _os.urandom(16)

now_ms = int(time.time() * 1000)

# Find an existing permission to copy the created_by / modified_by fields
cur.execute("SELECT created_by, scope_id FROM access_permission WHERE access_info_id = %s LIMIT 1",
            (access_info_id,))
ref = cur.fetchone()
created_by = ref[0] if ref else scope_id

new_perm_id = new_id()
cur.execute("""
    INSERT INTO access_permission
        (id, scope_id, created_on, created_by, modified_on, modified_by, optlock,
         access_info_id, domain_id, action, target_scope_id, forwardable)
    VALUES
        (%s, %s, %s, %s, %s, %s, 0,
         %s, %s, 2, NULL, 0)
""", (new_perm_id, scope_id, now_ms, created_by, now_ms, created_by,
      access_info_id, domain_id))
conn.commit()
print(f"SUCCESS: granted broker:read to '{TARGET_USER}' (perm id={new_perm_id.hex()})")
conn.close()
