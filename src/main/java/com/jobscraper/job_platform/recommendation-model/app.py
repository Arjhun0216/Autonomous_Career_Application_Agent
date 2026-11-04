from fastapi import FastAPI
from sentence_transformers import SentenceTransformer

app = FastAPI(
    title="Job Recommendation Embedding Service"
)

model = SentenceTransformer("all-MiniLM-L6-v2")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.get("/embed")
def generate_embedding(text: str):

    print("==========================================")
    print("EMBEDDING REQUEST RECEIVED")
    print("TEXT:", text)

    text = text.strip()

    if not text:
        return {"embedding": []}

    embedding = model.encode(
        text,
        normalize_embeddings=True
    )

    result = embedding.tolist()

    print("EMBEDDING DIMENSION:", len(result))
    print("==========================================")

    return {
        "embedding": result
    }