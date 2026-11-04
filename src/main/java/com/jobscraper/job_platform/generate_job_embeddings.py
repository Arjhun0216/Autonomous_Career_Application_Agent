import json
import requests
import mysql.connector

# ===== CONFIG =====
DB_HOST = "localhost"
DB_PORT = 3306
DB_USER = "root"
DB_PASSWORD = ""          # XAMPP default is usually empty
DB_NAME = "job_platform"

EMBEDDING_URL = "http://localhost:8001/embed"

# ===== CONNECT MYSQL =====
conn = mysql.connector.connect(
    host=DB_HOST,
    port=DB_PORT,
    user=DB_USER,
    password=DB_PASSWORD,
    database=DB_NAME
)

cursor = conn.cursor(dictionary=True)

cursor.execute("""
    SELECT job_id, title, company, experience_level, jd, location, salary, skills
    FROM jobs
    WHERE embedding IS NULL OR embedding = '' OR embedding = '[]'
    ORDER BY job_id
""")

jobs = cursor.fetchall()

print(f"Jobs needing embeddings: {len(jobs)}")

if not jobs:
    print("No jobs need embedding generation.")
    cursor.close()
    conn.close()
    raise SystemExit

update_sql = """
    UPDATE jobs
    SET embedding = %s
    WHERE job_id = %s
"""

for index, job in enumerate(jobs, start=1):

    # This is the text that gets converted into the job embedding.
    job_text = f"""
Title: {job['title'] or ''}
Company: {job['company'] or ''}
Experience: {job['experience_level'] or ''}
Skills: {job['skills'] or ''}
Location: {job['location'] or ''}
Salary: {job['salary'] or ''}
Job Description: {job['jd'] or ''}
""".strip()

    try:
        response = requests.post(
            EMBEDDING_URL,
            json={"text": job_text},
            timeout=120
        )
        response.raise_for_status()

        data = response.json()
        embedding = data.get("embedding", [])

        if len(embedding) != 384:
            print(
                f"[{index}/{len(jobs)}] {job['job_id']} FAILED "
                f"-> expected 384 dimensions, got {len(embedding)}"
            )
            continue

        embedding_json = json.dumps(embedding)

        cursor.execute(
            update_sql,
            (embedding_json, job["job_id"])
        )
        conn.commit()

        print(
            f"[{index}/{len(jobs)}] {job['job_id']} "
            f"OK -> {len(embedding)} dimensions"
        )

    except Exception as e:
        print(f"[{index}/{len(jobs)}] {job['job_id']} FAILED -> {e}")

cursor.close()
conn.close()

print("\nEmbedding generation completed.")
