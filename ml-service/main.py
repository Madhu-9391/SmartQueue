"""
SmartQueue — Python ML Microservice
====================================
FastAPI service that serves the Random Forest prediction model.
Spring Boot calls POST /predict — this returns the predicted
consultation wait time with confidence bounds.

Run:
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000 --reload

Endpoints:
    POST /predict          → predict wait time for one appointment
    POST /predict/batch    → predict for multiple appointments
    POST /train            → retrain model from DB history
    GET  /model/info       → model metadata
    GET  /health           → health check
"""

from __future__ import annotations

import os
import json
import pickle
import logging
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from sklearn.linear_model import LinearRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.preprocessing import StandardScaler
import uvicorn

# ─── Logging ──────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("smartqueue.ml")

app = FastAPI(
    title="SmartQueue ML Microservice",
    description="Random Forest ETA prediction for hospital queue management",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:3000"],
    allow_methods=["*"],
    allow_headers=["*"],
)

MODEL_PATH  = Path("models/queue_predictor.pkl")
SCALER_PATH = Path("models/scaler.pkl")
META_PATH   = Path("models/metadata.json")

# ─── Pydantic Schemas ─────────────────────────────────────────

class PredictRequest(BaseModel):
    doctor_id: int
    queue_position: int          = Field(..., ge=1, le=100)
    doctor_avg_speed: float      = Field(..., description="Minutes per patient (historical)")
    doctor_delay_today: int      = Field(default=0, ge=0, le=120)
    time_of_day: int             = Field(..., ge=0, le=23, description="Hour of day 0-23")
    day_of_week: int             = Field(..., ge=1, le=7,  description="1=Mon … 7=Sun")
    emergency_cases_before: int  = Field(default=0, ge=0, le=10)
    patient_priority: str        = Field(default="NORMAL",
                                         pattern="^(EMERGENCY|VIP|SENIOR_CITIZEN|NORMAL)$")
    no_show_probability: float   = Field(default=0.08, ge=0.0, le=1.0)
    department_load: int         = Field(default=5, ge=0, le=50)

class PredictResponse(BaseModel):
    predicted_wait_minutes: int
    predicted_visit_time: str       # ISO-8601
    confidence_lower_minutes: int
    confidence_upper_minutes: int
    confidence_window: int          # ±N minutes
    model_name: str
    model_version: str

class BatchPredictRequest(BaseModel):
    requests: list[PredictRequest]

class TrainRequest(BaseModel):
    training_data: list[dict]       # rows from consultation_history
    model_type: str = Field(default="random_forest",
                            pattern="^(random_forest|gradient_boost|linear)$")

class ModelInfo(BaseModel):
    model_type: str
    version: str
    trained_at: str
    training_samples: int
    mae_minutes: float
    rmse_minutes: float
    feature_names: list[str]
    feature_importances: dict[str, float]

# ─── Feature Engineering ──────────────────────────────────────

PRIORITY_MAP = {"EMERGENCY": 0, "VIP": 1, "SENIOR_CITIZEN": 2, "NORMAL": 3}

FEATURE_NAMES = [
    "queue_position",
    "doctor_avg_speed",
    "doctor_delay_today",
    "time_of_day",
    "day_of_week",
    "emergency_cases_before",
    "patient_priority_encoded",
    "no_show_probability",
    "department_load",
    "queue_x_speed",             # interaction feature
    "is_peak_hours",             # 9-11am, 2-4pm
    "adjusted_speed",            # speed + delay fraction
]

def build_features(req: PredictRequest) -> np.ndarray:
    """Convert PredictRequest into ML feature vector."""
    priority_enc = PRIORITY_MAP.get(req.patient_priority, 3)
    queue_x_speed = req.queue_position * req.doctor_avg_speed
    is_peak = 1 if req.time_of_day in range(9, 12) or req.time_of_day in range(14, 17) else 0
    adjusted_speed = req.doctor_avg_speed + (req.doctor_delay_today * 0.1)

    return np.array([[
        req.queue_position,
        req.doctor_avg_speed,
        req.doctor_delay_today,
        req.time_of_day,
        req.day_of_week,
        req.emergency_cases_before,
        priority_enc,
        req.no_show_probability,
        req.department_load,
        queue_x_speed,
        is_peak,
        adjusted_speed,
    ]])

# ─── Model Loading / Fallback ─────────────────────────────────

class ModelRegistry:
    model: Optional[RandomForestRegressor] = None
    scaler: Optional[StandardScaler] = None
    metadata: dict = {}

    def load(self):
        if MODEL_PATH.exists() and SCALER_PATH.exists():
            with open(MODEL_PATH, "rb") as f:
                self.model = pickle.load(f)
            with open(SCALER_PATH, "rb") as f:
                self.scaler = pickle.load(f)
            if META_PATH.exists():
                self.metadata = json.loads(META_PATH.read_text())
            log.info("Loaded model from %s", MODEL_PATH)
        else:
            log.warning("No saved model found — training fallback model")
            self._train_fallback()

    def _train_fallback(self):
        """Train a minimal model on synthetic data so service is always ready."""
        log.info("Training fallback model on synthetic data...")
        np.random.seed(42)
        n = 500

        positions = np.random.randint(1, 20, n)
        speeds    = np.random.uniform(8, 22, n)
        delays    = np.random.randint(0, 30, n)
        hours     = np.random.randint(8, 18, n)
        dows      = np.random.randint(1, 8, n)
        emergs    = np.random.randint(0, 4, n)
        priorities= np.random.choice([0, 1, 2, 3], n, p=[0.05, 0.1, 0.15, 0.7])
        no_shows  = np.random.uniform(0.04, 0.12, n)
        loads     = np.random.randint(2, 15, n)

        queue_x_speed  = positions * speeds
        is_peak        = ((hours >= 9) & (hours <= 11)) | ((hours >= 14) & (hours <= 16))
        adjusted_speed = speeds + delays * 0.1

        X = np.column_stack([
            positions, speeds, delays, hours, dows, emergs,
            priorities, no_shows, loads, queue_x_speed,
            is_peak.astype(int), adjusted_speed,
        ])

        noise = np.random.normal(0, 3, n)
        y = (positions * speeds + emergs * 12 - positions * no_shows * speeds + delays + noise).clip(2)

        self.scaler = StandardScaler()

        X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42)
        self.scaler = StandardScaler()
        X_tr = self.scaler.fit_transform(X_tr)
        X_te = self.scaler.transform(X_te)

        self.model = RandomForestRegressor(
            n_estimators=100, max_depth=8, min_samples_leaf=3,
            random_state=42, n_jobs=-1,
        )
        self.model.fit(X_tr, y_tr)

        MODEL_PATH.parent.mkdir(exist_ok=True)
        with open(MODEL_PATH, "wb") as f: pickle.dump(self.model, f)
        with open(SCALER_PATH, "wb") as f: pickle.dump(self.scaler, f)

        # Evaluate only on held-out data
        preds = self.model.predict(X_te)
        mae  = float(mean_absolute_error(y_te, preds))
        rmse = float(np.sqrt(mean_squared_error(y_te, preds)))

        importances = dict(zip(FEATURE_NAMES, self.model.feature_importances_.tolist()))
        self.metadata = {
            "model_type": "random_forest_fallback",
            "version": "1.0.0-fallback",
            "trained_at": datetime.utcnow().isoformat(),
            "training_samples": n,
            "mae_minutes": round(mae, 2),
            "rmse_minutes": round(rmse, 2),
            "feature_names": FEATURE_NAMES,
            "feature_importances": importances,
        }
        META_PATH.write_text(json.dumps(self.metadata, indent=2))
        log.info("Fallback model ready. MAE=%.2fm, RMSE=%.2fm", mae, rmse)

    def predict_one(self, req: PredictRequest) -> tuple[float, float]:
        """Returns (predicted_wait_minutes, confidence_stddev)."""
        if self.model is None:
            raise RuntimeError("Model not loaded")
        X = build_features(req)
        if self.scaler:
            X = self.scaler.transform(X)

        # For Random Forest: use per-tree predictions for confidence
        if hasattr(self.model, "estimators_"):
            tree_preds = np.array([tree.predict(X)[0] for tree in self.model.estimators_])
            mean_pred  = float(np.mean(tree_preds))
            std_pred   = float(np.std(tree_preds))
        else:
            mean_pred = float(self.model.predict(X)[0])
            std_pred  = 5.0  # fallback

        return max(2.0, mean_pred), max(3.0, std_pred)


registry = ModelRegistry()


@app.on_event("startup")
async def startup():
    registry.load()
    log.info("SmartQueue ML service ready")


# ─── Endpoints ────────────────────────────────────────────────

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_loaded": registry.model is not None,
        "model_version": registry.metadata.get("version", "unknown"),
        "timestamp": datetime.utcnow().isoformat(),
    }


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    """
    Main prediction endpoint.
    Called by Spring Boot AppointmentService after each booking or queue event.
    """
    try:
        wait_mins, std = registry.predict_one(req)
    except Exception as e:
        log.error("Prediction failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))

    # Clamp confidence to a sensible range
    conf_window = max(3, min(20, int(round(std * 1.5))))

    visit_time = datetime.now().astimezone() + timedelta(minutes=wait_mins)

    return PredictResponse(
        predicted_wait_minutes=int(round(wait_mins)),
        predicted_visit_time=visit_time.isoformat(),
        confidence_lower_minutes=max(2, int(round(wait_mins - conf_window))),
        confidence_upper_minutes=int(round(wait_mins + conf_window)),
        confidence_window=conf_window,
        model_name=registry.metadata.get("model_type", "random_forest"),
        model_version=registry.metadata.get("version", "1.0.0"),
    )


@app.post("/predict/batch")
def predict_batch(body: BatchPredictRequest):
    """Predict for multiple appointments at once (queue recalculation)."""
    results = []
    for req in body.requests:
        try:
            wait_mins, std = registry.predict_one(req)
            conf_window = max(3, min(20, int(round(std * 1.5))))
            visit_time  = datetime.now().astimezone() + timedelta(minutes=wait_mins)
            results.append({
                "predicted_wait_minutes": int(round(wait_mins)),
                "predicted_visit_time": visit_time.isoformat(),
                "confidence_window": conf_window,
            })
        except Exception as e:
            results.append({"error": str(e)})
    return {"predictions": results}


@app.post("/train")
def train_model(req: TrainRequest):
    """
    Retrain the model using fresh data from consultation_history.
    Spring Boot admin controller can call this nightly or on-demand.
    """
    data = req.training_data
    if len(data) < 50:
        raise HTTPException(status_code=400, detail="Need at least 50 training records")

    df = pd.DataFrame(data)
    required = ["queue_position", "doctor_avg_speed", "doctor_delay_today",
                "time_of_day", "day_of_week", "emergency_cases_before",
                "patient_priority", "no_show_probability",
                "department_load", "consultation_duration_minutes"]
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise HTTPException(status_code=400, detail=f"Missing columns: {missing}")

    df["priority_enc"]     = df["patient_priority"].map(PRIORITY_MAP).fillna(3)
    df["queue_x_speed"]    = df["queue_position"] * df["doctor_avg_speed"]
    df["is_peak"]          = df["time_of_day"].apply(lambda h: 1 if 9 <= h <= 11 or 14 <= h <= 16 else 0)
    df["adjusted_speed"]   = df["doctor_avg_speed"] + df["doctor_delay_today"] * 0.1

    feature_cols = [
        "queue_position", "doctor_avg_speed", "doctor_delay_today",
        "time_of_day", "day_of_week", "emergency_cases_before",
        "priority_enc", "no_show_probability", "department_load",
        "queue_x_speed", "is_peak", "adjusted_speed",
    ]
    X = df[feature_cols].values
    y = df["consultation_duration_minutes"].clip(lower=2).values

    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42)

    scaler = StandardScaler()
    X_tr = scaler.fit_transform(X_tr)
    X_te = scaler.transform(X_te)

    model_map = {
        "random_forest":   RandomForestRegressor(n_estimators=200, max_depth=10, n_jobs=-1, random_state=42),
        "gradient_boost":  GradientBoostingRegressor(n_estimators=200, max_depth=6, random_state=42),
        "linear":          LinearRegression(),
    }
    model = model_map[req.model_type]
    model.fit(X_tr, y_tr)

    preds = model.predict(X_te)
    mae   = float(mean_absolute_error(y_te, preds))
    rmse  = float(np.sqrt(mean_squared_error(y_te, preds)))

    # Persist
    with open(MODEL_PATH, "wb") as f: pickle.dump(model, f)
    with open(SCALER_PATH, "wb") as f: pickle.dump(scaler, f)

    importances = {}
    if hasattr(model, "feature_importances_"):
        importances = dict(zip(FEATURE_NAMES, model.feature_importances_.tolist()))

    metadata = {
        "model_type": req.model_type,
        "version": f"2.0.0-{req.model_type}",
        "trained_at": datetime.utcnow().isoformat(),
        "training_samples": len(df),
        "mae_minutes": round(mae, 2),
        "rmse_minutes": round(rmse, 2),
        "feature_names": FEATURE_NAMES,
        "feature_importances": importances,
    }
    META_PATH.write_text(json.dumps(metadata, indent=2))

    registry.model    = model
    registry.scaler   = scaler
    registry.metadata = metadata

    log.info("Model retrained. Type=%s, MAE=%.2fm, RMSE=%.2fm, n=%d",
             req.model_type, mae, rmse, len(df))

    return {
        "success": True,
        "model_type": req.model_type,
        "mae_minutes": round(mae, 2),
        "rmse_minutes": round(rmse, 2),
        "training_samples": len(df),
    }


@app.get("/model/info", response_model=ModelInfo)
def model_info():
    if not registry.metadata:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return ModelInfo(**registry.metadata)


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
