from contextlib import asynccontextmanager
import os

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import (
    TOKEN_TTL_HOURS,
    authenticate_user,
    create_access_token,
    get_current_user,
    require_operator,
)
from app.db import Base, engine, ensure_schema_updates, get_db, wait_for_database
from app.domain import ORDER_STATUS_TRANSITIONS, OrderStatus
from app.models import User
from app.schemas import (
    AddCommentRequest,
    AuthSessionResponse,
    CreateOrderRequest,
    HealthResponse,
    LoginRequest,
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
    ensure_schema_updates()
    with Session(engine) as session:
        seed_initial_data(session)
    yield


app = FastAPI(
    title="StatusFlow API",
    version="0.2.0",
    description="Source of truth for workflow status transitions.",
    lifespan=lifespan,
)

default_origins = ",".join(
    [
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:8080",
        "http://127.0.0.1:8080",
    ]
)
cors_origins = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", default_origins).split(",")
    if origin.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
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


@app.post("/auth/login", response_model=AuthSessionResponse, tags=["auth"])
def login(
    payload: LoginRequest,
    db: Session = Depends(get_db),
) -> AuthSessionResponse:
    user = authenticate_user(db, payload.email, payload.password)
    if user is None:
        from fastapi import HTTPException, status

        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password.",
        )

    return AuthSessionResponse(
        access_token=create_access_token(user),
        expires_in_seconds=TOKEN_TTL_HOURS * 60 * 60,
        user=user,
    )


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
def list_users(
    _: UserSummary = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[User]:
    return db.scalars(select(User).order_by(User.role, User.name)).all()


@app.get("/orders", response_model=list[OrderSummary], tags=["orders"])
def get_orders(
    status: OrderStatus | None = None,
    _: UserSummary = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[OrderSummary]:
    return list_orders(db, status)


@app.get("/orders/{order_id}", response_model=OrderDetail, tags=["orders"])
def get_order(
    order_id: str,
    _: UserSummary = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> OrderDetail:
    return get_order_or_404(db, order_id)


@app.post("/orders", response_model=OrderDetail, status_code=201, tags=["orders"])
def create_order_endpoint(
    payload: CreateOrderRequest,
    _: UserSummary = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> OrderDetail:
    return create_order(db, payload)


@app.post("/orders/{order_id}/comments", response_model=OrderDetail, tags=["orders"])
def add_comment_endpoint(
    order_id: str,
    payload: AddCommentRequest,
    _: UserSummary = Depends(require_operator),
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
    _: UserSummary = Depends(require_operator),
    db: Session = Depends(get_db),
) -> OrderDetail:
    return transition_order_status(db, order_id, payload)
