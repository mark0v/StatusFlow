from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import hash_password
from app.domain import OrderStatus, UserRole
from app.models import Order, OrderComment, OrderStatusHistory, User


def _seed_users(session: Session) -> dict[str, User]:
    existing = session.scalars(select(User)).all()
    if existing:
        return {user.role.value: user for user in existing}

    customer = User(
        email="customer@example.com",
        password_hash=hash_password("customer123"),
        name="Alex Morgan",
        role=UserRole.CUSTOMER,
    )
    operator = User(
        email="operator@example.com",
        password_hash=hash_password("operator123"),
        name="Riley Chen",
        role=UserRole.OPERATOR,
    )
    session.add_all([customer, operator])
    session.flush()
    return {customer.role.value: customer, operator.role.value: operator}


def _backfill_password_hashes(session: Session, users: list[User]) -> None:
    defaults = {
        UserRole.CUSTOMER: "customer123",
        UserRole.OPERATOR: "operator123",
    }
    updated = False
    for user in users:
        if user.password_hash:
            continue
        user.password_hash = hash_password(defaults[user.role])
        updated = True
    if updated:
        session.flush()


def seed_initial_data(session: Session) -> None:
    users = _seed_users(session)
    _backfill_password_hashes(session, list(users.values()))

    has_orders = session.scalar(select(Order.id).limit(1))
    if has_orders:
        session.commit()
        return

    customer = users[UserRole.CUSTOMER.value]
    operator = users[UserRole.OPERATOR.value]

    orders = [
        Order(
            code="SF-1001",
            title="Replace display unit",
            description="Customer reported a cracked screen after transit.",
            customer_name=customer.name,
            customer_id=customer.id,
            status=OrderStatus.NEW,
        ),
        Order(
            code="SF-1002",
            title="Verify warranty documents",
            description="Operator needs to validate proof of purchase.",
            customer_name=customer.name,
            customer_id=customer.id,
            status=OrderStatus.IN_REVIEW,
        ),
        Order(
            code="SF-1003",
            title="Prepare approved shipment",
            description="Replacement item approved and waiting for dispatch.",
            customer_name=customer.name,
            customer_id=customer.id,
            status=OrderStatus.APPROVED,
        ),
    ]
    session.add_all(orders)
    session.flush()

    session.add_all(
        [
            OrderStatusHistory(
                order_id=orders[0].id,
                changed_by_id=customer.id,
                from_status=None,
                to_status=OrderStatus.NEW,
                reason="Order created from mobile client.",
            ),
            OrderStatusHistory(
                order_id=orders[1].id,
                changed_by_id=operator.id,
                from_status=OrderStatus.NEW,
                to_status=OrderStatus.IN_REVIEW,
                reason="Operator started manual review.",
            ),
            OrderStatusHistory(
                order_id=orders[2].id,
                changed_by_id=operator.id,
                from_status=OrderStatus.IN_REVIEW,
                to_status=OrderStatus.APPROVED,
                reason="Documentation approved.",
            ),
            OrderComment(
                order_id=orders[1].id,
                author_id=operator.id,
                body="Waiting for one more document from the customer.",
            ),
            OrderComment(
                order_id=orders[2].id,
                author_id=operator.id,
                body="Approved for fulfillment queue.",
            ),
        ]
    )
    session.commit()
