from datetime import UTC, datetime

from pydantic import BaseModel, ConfigDict, Field

from app.domain import OrderStatus, UserRole


class HealthResponse(BaseModel):
    status: str = "ok"
    service: str = "statusflow-api"
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))


class UserSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: str
    name: str
    role: UserRole


class OrderCommentResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    body: str
    created_at: datetime
    author: UserSummary


class OrderStatusHistoryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    from_status: OrderStatus | None
    to_status: OrderStatus
    reason: str
    changed_at: datetime
    changed_by: UserSummary


class OrderSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    code: str
    title: str
    customer_name: str
    status: OrderStatus
    updated_at: datetime


class OrderDetail(OrderSummary):
    description: str
    customer: UserSummary
    comments: list[OrderCommentResponse]
    history: list[OrderStatusHistoryResponse]


class CreateOrderRequest(BaseModel):
    title: str = Field(min_length=3, max_length=160)
    description: str = Field(default="", max_length=2000)
    customer_id: str


class AddCommentRequest(BaseModel):
    author_id: str
    body: str = Field(min_length=1, max_length=2000)


class TransitionOrderStatusRequest(BaseModel):
    changed_by_id: str
    to_status: OrderStatus
    reason: str = Field(default="", max_length=1000)


class OrderStatusLifecycleResponse(BaseModel):
    statuses: list[OrderStatus]
    allowed_transitions: dict[OrderStatus, list[OrderStatus]]
