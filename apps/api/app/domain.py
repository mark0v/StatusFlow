from enum import StrEnum


class UserRole(StrEnum):
    CUSTOMER = "customer"
    OPERATOR = "operator"


class OrderStatus(StrEnum):
    NEW = "new"
    IN_REVIEW = "in_review"
    APPROVED = "approved"
    REJECTED = "rejected"
    FULFILLED = "fulfilled"
    CANCELLED = "cancelled"


ORDER_STATUS_TRANSITIONS: dict[OrderStatus, tuple[OrderStatus, ...]] = {
    OrderStatus.NEW: (OrderStatus.IN_REVIEW, OrderStatus.CANCELLED),
    OrderStatus.IN_REVIEW: (
        OrderStatus.APPROVED,
        OrderStatus.REJECTED,
        OrderStatus.CANCELLED,
    ),
    OrderStatus.APPROVED: (OrderStatus.FULFILLED, OrderStatus.CANCELLED),
    OrderStatus.REJECTED: (OrderStatus.IN_REVIEW, OrderStatus.CANCELLED),
    OrderStatus.FULFILLED: (),
    OrderStatus.CANCELLED: (),
}
