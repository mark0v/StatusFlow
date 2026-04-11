import { isAuthFailureMessage, loadDashboardData, loadOrderDetail } from "./webApi";
import {
  orderedStatuses,
  statusLabels,
  type AuthSession,
  type DashboardData,
  type OrderCard,
  type OrderDetail,
  type OrderStatus,
  type SortDirection,
  type SortField,
  type UserSummary
} from "./webTypes";
import type { CachedConsoleSnapshot } from "./webCacheStore";

export type DashboardSyncResult = {
  dashboard: DashboardData;
  lastRefreshedAt: string;
  selectedOrderId: string | null;
  selectedOrderDetail: OrderDetail | null;
  detailError: string | null;
};

export type DetailSyncResult = {
  selectedOrderDetail: OrderDetail | null;
  detailError: string | null;
};

export type SyncSource = "live" | "cached";

export function resolveCurrentCustomer(users: UserSummary[], session: AuthSession | null) {
  const customer = users.find((user) => user.role === "customer") ?? null;

  if (!session) {
    return customer;
  }

  if (session.user.role === "customer") {
    return (
      users.find((user) => user.email === session.user.email) ??
      users.find((user) => user.id === session.user.id) ??
      customer
    );
  }

  return customer;
}

export function resolveRecoveryCandidateOrder(
  orders: OrderCard[],
  selectedOrderId: string | null
) {
  return orders.find((order) => order.id !== selectedOrderId) ?? null;
}

export function getGroupedStatuses(orders: OrderCard[]) {
  const counts = orders.reduce<Record<OrderStatus, number>>((accumulator, order) => {
    accumulator[order.status] = (accumulator[order.status] ?? 0) + 1;
    return accumulator;
  }, {} as Record<OrderStatus, number>);

  return orderedStatuses.map((status) => [status, counts[status] ?? 0] as const);
}

export function filterOrders(
  orders: OrderCard[],
  searchQuery: string,
  statusFilter: OrderStatus[]
) {
  const normalizedQuery = searchQuery.trim().toLowerCase();

  return orders.filter((order) => {
    const matchesStatus = statusFilter.length === 0 || statusFilter.includes(order.status);
    if (!matchesStatus) {
      return false;
    }

    if (!normalizedQuery) {
      return true;
    }

    return [order.code, order.title, order.customer_name].some((value) =>
      value.toLowerCase().includes(normalizedQuery)
    );
  });
}

export function sortOrders(
  orders: OrderCard[],
  sortField: SortField,
  sortDirection: SortDirection
) {
  const next = [...orders];

  next.sort((left, right) => {
    if (sortField === "status") {
      const result = statusLabels[left.status].localeCompare(statusLabels[right.status]);
      return sortDirection === "asc" ? result : -result;
    }

    if (sortField === "customer_name") {
      const result = left.customer_name.localeCompare(right.customer_name);
      return sortDirection === "asc" ? result : -result;
    }

    const result = new Date(left.updated_at).getTime() - new Date(right.updated_at).getTime();
    return sortDirection === "asc" ? result : -result;
  });

  return next;
}

export function restoreCachedDashboardSyncResult(
  cachedSnapshot: CachedConsoleSnapshot,
  preferredSelectedOrderId?: string | null
): DashboardSyncResult {
  const selectedOrderId = resolveSelectedOrderId(
    cachedSnapshot.dashboard.orders,
    preferredSelectedOrderId ?? cachedSnapshot.selectedOrderId
  );

  return {
    dashboard: cachedSnapshot.dashboard,
    lastRefreshedAt: cachedSnapshot.lastRefreshedAt,
    selectedOrderId,
    selectedOrderDetail: selectedOrderId
      ? cachedSnapshot.detailsByOrderId[selectedOrderId] ?? null
      : null,
    detailError: null
  };
}

export function restoreCachedDetailSyncResult(
  cachedSnapshot: CachedConsoleSnapshot,
  selectedOrderId: string
): DetailSyncResult | null {
  const cachedDetail = cachedSnapshot.detailsByOrderId[selectedOrderId];

  if (!cachedDetail) {
    return null;
  }

  return {
    selectedOrderDetail: cachedDetail,
    detailError: null
  };
}

function resolveSelectedOrderId(
  orders: OrderCard[],
  preferredSelectedOrderId?: string | null
) {
  if (preferredSelectedOrderId && orders.some((order) => order.id === preferredSelectedOrderId)) {
    return preferredSelectedOrderId;
  }

  return orders[0]?.id ?? null;
}

export async function syncDashboardAndDetail(
  accessToken: string,
  preferredSelectedOrderId?: string | null,
  signal?: AbortSignal
): Promise<DashboardSyncResult> {
  const dashboard = await loadDashboardData(accessToken, signal);
  const selectedOrderId = resolveSelectedOrderId(dashboard.orders, preferredSelectedOrderId);

  if (!selectedOrderId) {
    return {
      dashboard,
      lastRefreshedAt: new Date().toISOString(),
      selectedOrderId: null,
      selectedOrderDetail: null,
      detailError: null
    };
  }

  try {
    const selectedOrderDetail = await loadOrderDetail(selectedOrderId, accessToken, signal);
    return {
      dashboard,
      lastRefreshedAt: new Date().toISOString(),
      selectedOrderId,
      selectedOrderDetail,
      detailError: null
    };
  } catch (detailFetchError) {
    if (detailFetchError instanceof Error && isAuthFailureMessage(detailFetchError.message)) {
      throw detailFetchError;
    }

    return {
      dashboard,
      lastRefreshedAt: new Date().toISOString(),
      selectedOrderId,
      selectedOrderDetail: null,
      detailError:
        detailFetchError instanceof Error
          ? detailFetchError.message
          : "Unable to load order detail."
    };
  }
}

export async function syncOrderDetail(
  accessToken: string,
  selectedOrderId: string,
  signal?: AbortSignal
): Promise<DetailSyncResult> {
  try {
    const selectedOrderDetail = await loadOrderDetail(selectedOrderId, accessToken, signal);
    return {
      selectedOrderDetail,
      detailError: null
    };
  } catch (fetchError) {
    if (fetchError instanceof Error && isAuthFailureMessage(fetchError.message)) {
      throw fetchError;
    }

    return {
      selectedOrderDetail: null,
      detailError: fetchError instanceof Error ? fetchError.message : "Unable to load order detail."
    };
  }
}
