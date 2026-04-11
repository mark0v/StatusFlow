import type { DashboardData, OrderDetail } from "./webTypes";

const CACHE_STORAGE_KEY = "statusflow.web.cache";

export type CachedConsoleSnapshot = {
  dashboard: DashboardData;
  lastRefreshedAt: string;
  selectedOrderId: string | null;
  detailsByOrderId: Record<string, OrderDetail>;
};

export function readCachedConsoleSnapshot() {
  const raw = window.localStorage.getItem(CACHE_STORAGE_KEY);

  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as CachedConsoleSnapshot;
  } catch {
    window.localStorage.removeItem(CACHE_STORAGE_KEY);
    return null;
  }
}

export function clearCachedConsoleSnapshot() {
  window.localStorage.removeItem(CACHE_STORAGE_KEY);
}

export function persistCachedConsoleSnapshot(
  input: Omit<CachedConsoleSnapshot, "detailsByOrderId"> & {
    selectedOrderDetail?: OrderDetail | null;
  }
) {
  const existing = readCachedConsoleSnapshot();
  const detailsByOrderId = {
    ...(existing?.detailsByOrderId ?? {})
  };

  if (input.selectedOrderDetail) {
    detailsByOrderId[input.selectedOrderDetail.id] = input.selectedOrderDetail;
  }

  window.localStorage.setItem(
    CACHE_STORAGE_KEY,
    JSON.stringify({
      dashboard: input.dashboard,
      lastRefreshedAt: input.lastRefreshedAt,
      selectedOrderId: input.selectedOrderId,
      detailsByOrderId
    } satisfies CachedConsoleSnapshot)
  );
}

export function persistCachedOrderDetail(orderDetail: OrderDetail, selectedOrderId: string | null) {
  const existing = readCachedConsoleSnapshot();

  if (!existing) {
    return;
  }

  window.localStorage.setItem(
    CACHE_STORAGE_KEY,
    JSON.stringify({
      ...existing,
      selectedOrderId,
      detailsByOrderId: {
        ...existing.detailsByOrderId,
        [orderDetail.id]: orderDetail
      }
    } satisfies CachedConsoleSnapshot)
  );
}
