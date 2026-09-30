from fastapi import FastAPI
app = FastAPI()
@app.get("/")
def health():
    return {"status": "ai-service running", "python": "3.13"}
