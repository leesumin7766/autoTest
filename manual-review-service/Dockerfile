FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN useradd --create-home --uid 10001 reviewer
COPY ocr_worker.py .
USER reviewer
# Prepare official models at build time. Runtime cannot access the internet.
RUN python ocr_worker.py --prepare
COPY . .
ENV HF_HUB_OFFLINE=1 HF_HUB_DISABLE_TELEMETRY=1 DO_NOT_TRACK=1 OCR_CPU_THREADS=4 OMP_NUM_THREADS=4 MKL_NUM_THREADS=4 PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True
EXPOSE 8007
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8007", "--workers", "1", "--no-access-log"]
