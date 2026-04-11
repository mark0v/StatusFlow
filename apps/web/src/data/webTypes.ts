export type OrderStatus =
  | "new"
  | "in_review"
  | "approved"
  | "rejected"
  | "fulfilled"
  | "cancelled";

export type OrderCard = {
  id: string;
  code: string;
  title: string;
  customer_name: string;
  status: OrderStatus;
  updated_at: string;
};

export type UserSummary = {
  id: string;
  email: string;
  name: string;
  role: "customer" | "operator";
};

export type OrderComment = {
  id: string;
  body: string;
  created_at: string;
  author: UserSummary;
};

export type OrderHistoryEvent = {
  id: string;
  from_status: OrderStatus | null;
  to_status: OrderStatus;
  reason: string;
  changed_at: string;
  changed_by: UserSummary;
};

export type OrderDetail = OrderCard & {
  description: string;
  comments: OrderComment[];
  history: OrderHistoryEvent[];
};

export type AuthSession = {
  access_token: string;
  token_type: "bearer";
  expires_in_seconds: number;
  user: UserSummary;
};

export type OrderStatusLifecycle = {
  statuses: OrderStatus[];
  allowed_transitions: Record<OrderStatus, OrderStatus[]>;
};

export type DashboardData = {
  orders: OrderCard[];
  users: UserSummary[];
  lifecycle: OrderStatusLifecycle;
};

export type CreateOrderFormState = {
  title: string;
  description: string;
};

export type SortField = "updated_at" | "status" | "customer_name";
export type SortDirection = "asc" | "desc";

export const statusLabels: Record<OrderStatus, string> = {
  new: "New",
  in_review: "In review",
  approved: "Approved",
  rejected: "Rejected",
  fulfilled: "Fulfilled",
  cancelled: "Cancelled"
};

export const orderedStatuses: OrderStatus[] = [
  "new",
  "in_review",
  "approved",
  "rejected",
  "fulfilled",
  "cancelled"
];

export function statusTone(status: OrderStatus) {
  switch (status) {
    case "new":
      return "tone-new";
    case "in_review":
      return "tone-review";
    case "approved":
      return "tone-approved";
    case "rejected":
      return "tone-rejected";
    case "fulfilled":
      return "tone-fulfilled";
    case "cancelled":
      return "tone-cancelled";
    default:
      return "tone-default";
  }
}

export function formatTimestamp(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

export function historySummary(event: OrderHistoryEvent) {
  return event.from_status
    ? `${statusLabels[event.from_status]} -> ${statusLabels[event.to_status]}`
    : `Created in ${statusLabels[event.to_status]}`;
}
