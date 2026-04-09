from datetime import UTC, datetime
from enum import StrEnum

from fastapi import FastAPI
from pydantic import BaseModel, Field


class OrderStatus(StrEnum):
    NEW = "new"
    IN_PROGRESS = "in_progress"
    READY = "ready"
    DELIVERED = "delivered"


class HealthResponse(BaseModel):
    status: str = "ok"
    service: str = "statusflow-api"
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))


class OrderSummary(BaseModel):
    id: str
    code: str
    customer_name: str
    status: OrderStatus
    updated_at: datetime


app = FastAPI(
    title="StatusFlow API",
    version="0.1.0",
    description="Source of truth for workflow status transitions.",
)

_orders = [
    OrderSummary(
        id="ord_1001",
        code="SF-1001",
        customer_name="Alex Morgan",
        status=OrderStatus.NEW,
        updated_at=datetime.now(UTC),
    ),
    OrderSummary(
        id="ord_1002",
        code="SF-1002",
        customer_name="Taylor Kim",
        status=OrderStatus.IN_PROGRESS,
        updated_at=datetime.now(UTC),
    ),
    OrderSummary(
        id="ord_1003",
        code="SF-1003",
        customer_name="Jordan Lee",
        status=OrderStatus.READY,
        updated_at=datetime.now(UTC),
    ),
]


@app.get("/", tags=["meta"])
def root() -> dict[str, str]:
    return {
        "name": "StatusFlow API",
        "docs": "/docs",
        "health": "/health",
        "orders": "/orders",
    }


@app.get("/health", response_model=HealthResponse, tags=["meta"])
def healthcheck() -> HealthResponse:
    return HealthResponse()


@app.get("/orders", response_model=list[OrderSummary], tags=["orders"])
def list_orders() -> list[OrderSummary]:
    return _orders
