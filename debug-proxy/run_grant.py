import pymysql, os, time

conn = pymysql.connect(
    host=os.environ.get("DB_HOST", "127.0.0.1"),
    port=int(os.environ.get("DB_PORT", "3307")),
    user=os.environ.get("DB_USER", "kapua"),
    password=os.environ.get("DB_PASS", "kapua"),
    database=os.environ.get("DB_NAME", "kapua"),
    charset="utf8",
)
cur = conn.cursor()

TARGET = os.environ.get("TARGET_BROKER_USER", "")
if not TARGET:
    raise SystemExit("TARGET_BROKER_USER is required")
cur.execute("SELECT id, name, scope_id FROM user WHERE name = %s", (TARGET,))
user = cur.fetchone()
if not user:
    print("ERROR: user not found"); exit(1)
user_id, user_name, scope_id = user
print(f"User: {user_name}  id={user_id.hex()}  scope={scope_id.hex()}")

cur.execute("SELECT id FROM access_info WHERE user_id=%s AND scope_id=%s", (user_id, scope_id))
ai = cur.fetchone()
if not ai:
    print("ERROR: no access_info"); exit(1)
ai_id = ai[0]
print(f"access_info id={ai_id.hex()}")

cur.execute("SELECT id FROM domain WHERE name='broker'")
dom = cur.fetchone()
dom_id = dom[0]
print(f"broker domain id={dom_id.hex()}")

cur.execute("SELECT id,action FROM access_permission WHERE access_info_id=%s AND domain_id=%s", (ai_id, dom_id))
perms = cur.fetchall()
print(f"Existing broker perms: {[(p[0].hex(), p[1]) for p in perms]}")

cur.execute("SELECT id FROM access_permission WHERE access_info_id=%s AND domain_id=%s AND action=2", (ai_id, dom_id))
if cur.fetchone():
    print("broker:read already exists, no change needed")
else:
    cur.execute("SELECT created_by FROM access_permission WHERE access_info_id=%s LIMIT 1", (ai_id,))
    ref = cur.fetchone()
    created_by = ref[0] if ref else scope_id
    now_ms = int(time.time()*1000)
    new_id = os.urandom(16)
    cur.execute("""INSERT INTO access_permission
        (id,scope_id,created_on,created_by,modified_on,modified_by,optlock,
         access_info_id,domain_id,action,target_scope_id,forwardable)
        VALUES (%s,%s,%s,%s,%s,%s,0,%s,%s,2,NULL,0)""",
        (new_id, scope_id, now_ms, created_by, now_ms, created_by, ai_id, dom_id))
    conn.commit()
    print(f"SUCCESS: broker:read granted  perm_id={new_id.hex()}")
conn.close()
