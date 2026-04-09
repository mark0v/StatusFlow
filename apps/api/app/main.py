from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import Base, engine, get_db, wait_for_database
from app.domain import ORDER_STATUS_TRANSITIONS, OrderStatus
from app.models import User
from app.schemas import (
    AddCommentRequest,
    CreateOrderRequest,
    HealthResponse,
    OrderDetail,
    OrderStatusLifecycleResponse,
    OrderSummary,
    TransitionOrderStatusRequest,
    UserSummary,
)
from app.seed import seed_initial_data
from app.services import add_comment, create_order, get_order_or_404, list_orders, transition_order_status


@asynccontextmanager
async def lifespan(_: FastAPI):
    wait_for_database()
    Base.metadata.create_all(bind=engine)
    with Session(engine) as session:
        seed_initial_data(session)
    yield


app = FastAPI(
    title="StatusFlow API",
    version="0.2.0",
    description="Source of truth for workflow status transitions.",
    lifespan=lifespan,
)


@app.get("/", tags=["meta"])
def root() -> dict[str, str]:
    return {
        "name": "StatusFlow API",
        "docs": "/docs",
        "health": "/health",
        "orders": "/orders",
        "status_lifecycle": "/order-status-lifecycle",
    }


@app.get("/health", response_model=HealthResponse, tags=["meta"])
def healthcheck() -> HealthResponse:
    return HealthResponse()


@app.get(
    "/order-status-lifecycle",
    response_model=OrderStatusLifecycleResponse,
    tags=["meta"],
)
def order_status_lifecycle() -> OrderStatusLifecycleResponse:
    return OrderStatusLifecycleResponse(
        statuses=list(OrderStatus),
        allowed_transitions={
            source: list(targets) for source, targets in ORDER_STATUS_TRANSITIONS.items()
        },
    )


@app.get("/users", response_model=list[UserSummary], tags=["users"])
def list_users(db: Session = Depends(get_db)) -> list[User]:
    return db.scalars(select(User).order_by(User.role, User.name)).all()


@app.get("/orders", response_model=list[OrderSummary], tags=["orders"])
def get_orders(
    status: OrderStatus | None = None,
    db: Session = Depends(get_db),
) -> list[OrderSummary]:
    return list_orders(db, status)


@app.get("/orders/{order_id}", response_model=OrderDetail, tags=["orders"])
def get_order(order_id: str, db: Session = Depends(get_db)) -> OrderDetail:
    return get_order_or_404(db, order_id)


@app.post("/orders", response_model=OrderDetail, status_code=201, tags=["orders"])
def create_order_endpoint(
    payload: CreateOrderRequest,
    db: Session = Depends(get_db),
) -> OrderDetail:
    return create_order(db, payload)


@app.post("/orders/{order_id}/comments", response_model=OrderDetail, tags=["orders"])
def add_comment_endpoint(
    order_id: str,
    payload: AddCommentRequest,
    db: Session = Depends(get_db),
) -> OrderDetail:
    return add_comment(db, order_id, payload)


@app.post(
    "/orders/{order_id}/status-transitions",
    response_model=OrderDetail,
    tags=["orders"],
)
def transition_order_status_endpoint(
    order_id: str,
    payload: TransitionOrderStatusRequest,
    db: Session = Depends(get_db),
) -> OrderDetail:
    return transition_order_status(db, order_id, payload)
