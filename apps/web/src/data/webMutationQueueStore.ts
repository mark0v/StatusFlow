import type {
  OrderCard,
  OrderComment,
  OrderDetail,
  OrderHistoryEvent,
  OrderStatus,
  UserSummary
} from "./webTypes";

const MUTATION_QUEUE_STORAGE_KEY = "statusflow.web.mutation-queue";

type QueuedMutationBase = {
  id: string;
  createdAt: string;
  userId: string;
};

export type QueuedCreateOrderMutation = QueuedMutationBase & {
  type: "create_order";
  tempOrderId: string;
  payload: {
    title: string;
    description: string;
    customerId: string;
    customerName: string;
    createdBy: UserSummary;
  };
};

export type QueuedStatusTransitionMutation = QueuedMutationBase & {
  type: "transition_status";
  payload: {
    orderId: string;
    toStatus: OrderStatus;
    reason: string;
    changedBy: UserSummary;
  };
};

export type QueuedCommentMutation = QueuedMutationBase & {
  type: "add_comment";
  payload: {
    orderId: string;
    body: string;
    author: UserSummary;
  };
};

export type QueuedMutation =
  | QueuedCreateOrderMutation
  | QueuedStatusTransitionMutation
  | QueuedCommentMutation;

function readStoredMutationQueue() {
  const raw = window.localStorage.getItem(MUTATION_QUEUE_STORAGE_KEY);

  if (!raw) {
    return [];
  }

  try {
    return JSON.parse(raw) as QueuedMutation[];
  } catch {
    window.localStorage.removeItem(MUTATION_QUEUE_STORAGE_KEY);
    return [];
  }
}

function writeStoredMutationQueue(queue: QueuedMutation[]) {
  window.localStorage.setItem(MUTATION_QUEUE_STORAGE_KEY, JSON.stringify(queue));
}

export function readQueuedMutations(userId: string | null) {
  if (!userId) {
    return [];
  }

  return readStoredMutationQueue().filter((mutation) => mutation.userId === userId);
}

export function replaceQueuedMutations(userId: string, nextUserQueue: QueuedMutation[]) {
  const queue = readStoredMutationQueue().filter((mutation) => mutation.userId !== userId);
  writeStoredMutationQueue([...queue, ...nextUserQueue]);
}

export function enqueueQueuedMutation(mutation: QueuedMutation) {
  writeStoredMutationQueue([...readStoredMutationQueue(), mutation]);
}

export function clearQueuedMutations(userId: string) {
  writeStoredMutationQueue(
    readStoredMutationQueue().filter((mutation) => mutation.userId !== userId)
  );
}

export function buildQueuedCreateOrderPreview(mutation: QueuedCreateOrderMutation): {
  order: OrderCard;
  detail: OrderDetail;
} {
  const order: OrderCard = {
    id: mutation.tempOrderId,
    code: `QUEUED-${mutation.id.slice(-4).toUpperCase()}`,
    title: mutation.payload.title,
    customer_name: mutation.payload.customerName,
    status: "new",
    updated_at: mutation.createdAt
  };

  return {
    order,
    detail: {
      ...order,
      description: mutation.payload.description,
      comments: [],
      history: [
        {
          id: `queued-history-${mutation.id}`,
          from_status: null,
          to_status: "new",
          reason: "Queued offline from the web console.",
          changed_at: mutation.createdAt,
          changed_by: mutation.payload.createdBy
        }
      ]
    }
  };
}

export function buildQueuedCommentPreview(mutation: QueuedCommentMutation): OrderComment {
  return {
    id: `queued-comment-${mutation.id}`,
    body: mutation.payload.body,
    created_at: mutation.createdAt,
    author: mutation.payload.author
  };
}

export function buildQueuedStatusHistoryEvent(
  mutation: QueuedStatusTransitionMutation,
  fromStatus: OrderStatus | null
): OrderHistoryEvent {
  return {
    id: `queued-status-${mutation.id}`,
    from_status: fromStatus,
    to_status: mutation.payload.toStatus,
    reason: mutation.payload.reason,
    changed_at: mutation.createdAt,
    changed_by: mutation.payload.changedBy
  };
}
