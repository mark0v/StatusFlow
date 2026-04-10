from datetime import UTC, datetime

from fastapi import HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session, joinedload

from app.domain import ORDER_STATUS_TRANSITIONS, OrderStatus
from app.models import Order, OrderComment, OrderStatusHistory, User
from app.schemas import AddCommentRequest, CreateOrderRequest, TransitionOrderStatusRequest


def order_query():
    return (
        select(Order)
        .options(
            joinedload(Order.customer),
            joinedload(Order.comments).joinedload(OrderComment.author),
            joinedload(Order.history).joinedload(OrderStatusHistory.changed_by),
        )
        .order_by(Order.updated_at.desc())
    )


def list_orders(session: Session, status_filter: OrderStatus | None = None) -> list[Order]:
    query = order_query()
    if status_filter is not None:
        query = query.where(Order.status == status_filter)
    return session.execute(query).unique().scalars().all()


def get_order_or_404(session: Session, order_id: str) -> Order:
    order = session.execute(order_query().where(Order.id == order_id)).unique().scalar_one_or_none()
    if order is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Order not found.")
    return order


def get_user_or_404(session: Session, user_id: str) -> User:
    user = session.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")
    return user


def next_order_code(session: Session) -> str:
    count = session.scalar(select(func.count()).select_from(Order)) or 0
    return f"SF-{1001 + count}"


def create_order(session: Session, payload: CreateOrderRequest) -> Order:
    customer = get_user_or_404(session, payload.customer_id)
    order = Order(
        code=next_order_code(session),
        title=payload.title,
        description=payload.description,
        customer_name=customer.name,
        customer_id=customer.id,
        status=OrderStatus.NEW,
        updated_at=datetime.now(UTC),
    )
    session.add(order)
    session.flush()
    session.add(
        OrderStatusHistory(
            order_id=order.id,
            changed_by_id=customer.id,
            from_status=None,
            to_status=OrderStatus.NEW,
            reason="Order created.",
        )
    )
    session.commit()
    return get_order_or_404(session, order.id)


def add_comment(session: Session, order_id: str, payload: AddCommentRequest) -> Order:
    order = get_order_or_404(session, order_id)
    author = get_user_or_404(session, payload.author_id)
    session.add(
        OrderComment(
            order_id=order.id,
            author_id=author.id,
            body=payload.body.strip(),
        )
    )
    order.updated_at = datetime.now(UTC)
    session.commit()
    return get_order_or_404(session, order_id)


def transition_order_status(
    session: Session,
    order_id: str,
    payload: TransitionOrderStatusRequest,
) -> Order:
    order = get_order_or_404(session, order_id)
    actor = get_user_or_404(session, payload.changed_by_id)
    allowed_targets = ORDER_STATUS_TRANSITIONS[order.status]

    if payload.to_status == order.status:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Order is already in the requested status.",
        )

    if payload.to_status not in allowed_targets:
        allowed = [status.value for status in allowed_targets]
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={
                "message": f"Invalid transition from {order.status.value} to {payload.to_status.value}.",
                "allowed_transitions": allowed,
            },
        )

    previous_status = order.status
    order.status = payload.to_status
    order.updated_at = datetime.now(UTC)
    session.add(
        OrderStatusHistory(
            order_id=order.id,
            changed_by_id=actor.id,
            from_status=previous_status,
            to_status=payload.to_status,
            reason=payload.reason.strip(),
        )
    )
    session.commit()
    return get_order_or_404(session, order_id)
