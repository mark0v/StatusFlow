import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  API_BASE_URL,
  FORBIDDEN_ERROR,
  addOrderComment,
  createOrder,
  isAuthFailureMessage,
  isOfflineQueueableError,
  login,
  NETWORK_UNAVAILABLE_ERROR,
  transitionOrderStatus
} from "./data/webApi";
import {
  clearCachedConsoleSnapshot,
  persistCachedConsoleSnapshot,
  persistCachedOrderDetail,
  readCachedConsoleSnapshot
} from "./data/webCacheStore";
import { persistSession, readStoredSession } from "./data/webSessionStore";
import {
  filterOrders,
  getGroupedStatuses,
  resolveCurrentCustomer,
  resolveRecoveryCandidateOrder,
  restoreCachedDashboardSyncResult,
  restoreCachedDetailSyncResult,
  sortOrders,
  syncDashboardAndDetail,
  syncOrderDetail
} from "./data/webSyncStore";
import {
  buildQueuedCommentPreview,
  buildQueuedCreateOrderPreview,
  buildQueuedStatusHistoryEvent,
  clearQueuedMutations,
  enqueueQueuedMutation,
  readQueuedMutations,
  replaceQueuedMutations,
  type QueuedCommentMutation,
  type QueuedCreateOrderMutation,
  type QueuedMutation,
  type QueuedStatusTransitionMutation
} from "./data/webMutationQueueStore";
import {
  statusLabels,
  type AuthSession,
  type CreateOrderFormState,
  type OrderCard,
  type OrderDetail,
  type OrderStatus,
  type OrderStatusLifecycle,
  type SortDirection,
  type SortField,
  type UserSummary
} from "./data/webTypes";
import { AuthScreen } from "./components/AuthScreen";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { Hero } from "./components/Hero";
import { OrderInspector } from "./components/OrderInspector";
import { OrderTable } from "./components/OrderTable";
import { StatusSummary } from "./components/StatusSummary";

type SyncSource = "live" | "cached";

export default function App() {
  const skipNextDetailBootstrapOrderId = useRef<string | null>(null);
  const [session, setSession] = useState<AuthSession | null>(() => readStoredSession());
  const [orders, setOrders] = useState<OrderCard[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [lifecycle, setLifecycle] = useState<OrderStatusLifecycle | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(session));
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authForm, setAuthForm] = useState({
    email: "operator@example.com",
    password: "operator123"
  });
  const [formState, setFormState] = useState<CreateOrderFormState>({
    title: "",
    description: ""
  });
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortField, setSortField] = useState<SortField>("updated_at");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [statusFilter, setStatusFilter] = useState<OrderStatus[]>([]);
  const [openActionsOrderId, setOpenActionsOrderId] = useState<string | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [selectedOrderDetail, setSelectedOrderDetail] = useState<OrderDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [commentDraft, setCommentDraft] = useState("");
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string | null>(null);
  const [syncSource, setSyncSource] = useState<SyncSource>("live");
  const [syncNotice, setSyncNotice] = useState<string | null>(null);
  const [pendingMutations, setPendingMutations] = useState<QueuedMutation[]>([]);
  const [queueNotice, setQueueNotice] = useState<string | null>(null);
  const [queueError, setQueueError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);

  const userInitials = useMemo(() => {
    if (!session?.user?.name) return "?";
    const trimmed = session.user.name.trim();
    if (!trimmed) return "?";
    const parts = trimmed.split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    return trimmed.slice(0, 2).toUpperCase();
  }, [session?.user?.name]);

  function applyDashboardState(data: {
    orders: OrderCard[];
    users: UserSummary[];
    lifecycle: OrderStatusLifecycle;
  }) {
    setOrders(data.orders);
    setUsers(data.users);
    setLifecycle(data.lifecycle);
  }

  function applyDetailState(nextDetail: OrderDetail | null, nextDetailError: string | null) {
    setSelectedOrderDetail(nextDetail);
    setDetailError(nextDetailError);
    if (nextDetail) {
      setCommentDraft("");
    }
  }

  function applyLiveSyncState(notice: string | null = null) {
    setSyncSource("live");
    setSyncNotice(notice);
    setError(null);
  }

  function applyCachedSyncState(notice: string) {
    setSyncSource("cached");
    setSyncNotice(notice);
    setError(null);
  }

  function restoreCachedDashboard(
    preferredSelectedOrderId: string | null | undefined,
    notice: string
  ) {
    const cachedSnapshot = readCachedConsoleSnapshot();
    if (!cachedSnapshot) {
      return false;
    }

    const nextState = restoreCachedDashboardSyncResult(cachedSnapshot, preferredSelectedOrderId);
    applyDashboardState(nextState.dashboard);
    setLastRefreshedAt(nextState.lastRefreshedAt);
    skipNextDetailBootstrapOrderId.current = nextState.selectedOrderId;
    setSelectedOrderId(nextState.selectedOrderId);
    applyDetailState(nextState.selectedOrderDetail, nextState.detailError);
    applyCachedSyncState(notice);
    return true;
  }

  function updateQueuedMutations(nextMutations: QueuedMutation[]) {
    if (session) {
      replaceQueuedMutations(session.user.id, nextMutations);
    }
    setPendingMutations(nextMutations);
  }

  function enqueueMutation(mutation: QueuedMutation) {
    enqueueQueuedMutation(mutation);
    setPendingMutations((current) => [...current, mutation]);
  }

  function queueMutationNotice(count: number) {
    return `${count} queued change${count === 1 ? "" : "s"} waiting for the next successful sync.`;
  }

  function getSyncErrorMessage(errorMessage: string) {
    if (errorMessage === NETWORK_UNAVAILABLE_ERROR) {
      return "Connection is unavailable. Queued changes will retry on the next refresh.";
    }
    return errorMessage;
  }

  async function flushPendingMutations(accessToken: string) {
    if (!session) {
      return;
    }

    const queuedMutations = readQueuedMutations(session.user.id);
    if (queuedMutations.length === 0) {
      setPendingMutations([]);
      return;
    }

    let remainingQueue = [...queuedMutations];
    let flushedCount = 0;
    const droppedErrors: string[] = [];

    while (remainingQueue.length > 0) {
      const [currentMutation, ...rest] = remainingQueue;

      try {
        if (currentMutation.type === "create_order") {
          await createOrder(accessToken, {
            title: currentMutation.payload.title,
            description: currentMutation.payload.description,
            customer_id: currentMutation.payload.customerId
          });
        } else if (currentMutation.type === "add_comment") {
          await addOrderComment(accessToken, currentMutation.payload.orderId, {
            author_id: currentMutation.payload.author.id,
            body: currentMutation.payload.body
          });
        } else {
          await transitionOrderStatus(accessToken, currentMutation.payload.orderId, {
            changed_by_id: currentMutation.payload.changedBy.id,
            to_status: currentMutation.payload.toStatus,
            reason: currentMutation.payload.reason
          });
        }
      } catch (flushError) {
        if (flushError instanceof Error && isAuthFailureMessage(flushError.message)) {
          throw flushError;
        }

        if (flushError instanceof Error && isOfflineQueueableError(flushError.message)) {
          updateQueuedMutations(remainingQueue);
          setQueueError(getSyncErrorMessage(flushError.message));
          if (flushedCount > 0) {
            setQueueNotice(`Synced ${flushedCount} queued change${flushedCount === 1 ? "" : "s"} before the connection dropped.`);
          }
          return;
        }

        droppedErrors.push(
          flushError instanceof Error
            ? flushError.message
            : "A queued change could not be applied."
        );
        remainingQueue = rest;
        continue;
      }

      flushedCount += 1;
      remainingQueue = rest;
    }

    updateQueuedMutations(remainingQueue);

    if (flushedCount > 0) {
      setQueueNotice(`Synced ${flushedCount} queued change${flushedCount === 1 ? "" : "s"}.`);
    }

    setQueueError(droppedErrors[0] ? `Dropped queued change: ${droppedErrors[0]}` : null);
  }

  async function refreshDashboardAndDetail(
    accessToken: string,
    preferredSelectedOrderId?: string | null,
    signal?: AbortSignal
  ) {
    setIsDetailLoading(true);
    try {
      await flushPendingMutations(accessToken);
      const nextState = await syncDashboardAndDetail(accessToken, preferredSelectedOrderId, signal);
      applyDashboardState(nextState.dashboard);
      setLastRefreshedAt(nextState.lastRefreshedAt);
      skipNextDetailBootstrapOrderId.current = nextState.selectedOrderId;
      setSelectedOrderId(nextState.selectedOrderId);
      applyDetailState(nextState.selectedOrderDetail, nextState.detailError);
      persistCachedConsoleSnapshot({
        dashboard: nextState.dashboard,
        lastRefreshedAt: nextState.lastRefreshedAt,
        selectedOrderId: nextState.selectedOrderId,
        selectedOrderDetail: nextState.selectedOrderDetail
      });
      applyLiveSyncState();
    } finally {
      setIsDetailLoading(false);
    }
  }

  function handleAuthExpired(message = "Your session expired. Sign in again.") {
    clearCachedConsoleSnapshot();
    if (session) {
      clearQueuedMutations(session.user.id);
    }
    persistSession(null);
    setSession(null);
    setOrders([]);
    setUsers([]);
    setLifecycle(null);
    setSelectedOrderId(null);
    setSelectedOrderDetail(null);
    setDetailError(null);
    setCommentDraft("");
    setOpenActionsOrderId(null);
    setIsCreateOpen(false);
    setIsRefreshing(false);
    setLastRefreshedAt(null);
    setSyncSource("live");
    setSyncNotice(null);
    setPendingMutations([]);
    setQueueNotice(null);
    setQueueError(null);
    setAuthError(message);
  }

  useEffect(() => {
    if (!session) {
      setPendingMutations([]);
      setQueueNotice(null);
      setQueueError(null);
      return;
    }
    setPendingMutations(readQueuedMutations(session.user.id));
  }, [session]);

  useEffect(() => {
    if (!session) {
      setIsLoading(false);
      return;
    }

    const accessToken = session.access_token;
    const controller = new AbortController();

    async function bootstrap() {
      try {
        setIsLoading(true);
        setError(null);
        await refreshDashboardAndDetail(
          accessToken,
          selectedOrderId,
          controller.signal
        );
      } catch (fetchError) {
        if (controller.signal.aborted) {
          return;
        }

        if (fetchError instanceof Error && isAuthFailureMessage(fetchError.message)) {
          handleAuthExpired(
            fetchError.message === FORBIDDEN_ERROR
              ? "Your access is no longer valid. Sign in again."
              : undefined
          );
          return;
        }

        if (restoreCachedDashboard(selectedOrderId, "Live sync failed. Showing the last successful snapshot.")) {
          return;
        }

        const message =
          fetchError instanceof Error
            ? fetchError.message
            : "Unknown error while loading dashboard data.";
        setError(message);
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      }
    }

    void bootstrap();

    return () => controller.abort();
  }, [session]);

  useEffect(() => {
    if (!session || !selectedOrderId) {
      setSelectedOrderDetail(null);
      setDetailError(null);
      setIsDetailLoading(false);
      return;
    }

    const accessToken = session.access_token;
    const activeOrderId = selectedOrderId;
    const controller = new AbortController();

    if (skipNextDetailBootstrapOrderId.current === activeOrderId) {
      skipNextDetailBootstrapOrderId.current = null;
      setIsDetailLoading(false);
      return () => controller.abort();
    }

    async function bootstrapDetail() {
      try {
        setIsDetailLoading(true);
        setDetailError(null);
        const nextDetailState = await syncOrderDetail(accessToken, activeOrderId, controller.signal);
        if (nextDetailState.selectedOrderDetail) {
          applyDetailState(nextDetailState.selectedOrderDetail, nextDetailState.detailError);
          persistCachedOrderDetail(nextDetailState.selectedOrderDetail, activeOrderId);
          if (syncSource !== "cached") {
            applyLiveSyncState();
          }
          return;
        }

        const cachedSnapshot = readCachedConsoleSnapshot();
        const cachedDetailState = cachedSnapshot
          ? restoreCachedDetailSyncResult(cachedSnapshot, activeOrderId)
          : null;

        if (cachedDetailState) {
          applyDetailState(cachedDetailState.selectedOrderDetail, cachedDetailState.detailError);
          applyCachedSyncState("Live detail failed. Showing the last successful order detail.");
          return;
        }

        applyDetailState(nextDetailState.selectedOrderDetail, nextDetailState.detailError);
      } catch (fetchError) {
        if (controller.signal.aborted) {
          return;
        }

        if (fetchError instanceof Error && isAuthFailureMessage(fetchError.message)) {
          handleAuthExpired(
            fetchError.message === FORBIDDEN_ERROR
              ? "Your access is no longer valid. Sign in again."
              : undefined
          );
          return;
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsDetailLoading(false);
        }
      }
    }

    void bootstrapDetail();

    return () => controller.abort();
  }, [selectedOrderId, session]);

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }

      if (!target.closest(".row-action-menu")) {
        setOpenActionsOrderId(null);
      }

      if (!target.closest(".user-menu-wrap")) {
        setIsUserMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  const groupedStatuses = useMemo(() => getGroupedStatuses(orders), [orders]);

  const operator = useMemo(
    () => users.find((user) => user.role === "operator") ?? null,
    [users]
  );
  const isOperator = session?.user.role === "operator";
  const consoleTitle = isOperator
    ? "Active orders"
    : "My orders";
  const currentCustomer = useMemo(() => resolveCurrentCustomer(users, session), [session, users]);
  const recoveryCandidateOrder = useMemo(
    () => resolveRecoveryCandidateOrder(orders, selectedOrderId),
    [orders, selectedOrderId]
  );
  const filteredOrders = useMemo(
    () => filterOrders(orders, searchQuery, statusFilter),
    [orders, searchQuery, statusFilter]
  );
  const sortedOrders = useMemo(
    () => sortOrders(filteredOrders, sortField, sortDirection),
    [filteredOrders, sortDirection, sortField]
  );
  const totalPages = Math.ceil(sortedOrders.length / pageSize);
  const paginatedOrders = useMemo(
    () => sortedOrders.slice((page - 1) * pageSize, page * pageSize),
    [sortedOrders, page, pageSize]
  );
  const pendingMutationCount = pendingMutations.length;
  const selectedOrderIsQueuedDraft = selectedOrderId?.startsWith("queued-order-") ?? false;

  useEffect(() => {
    if (totalPages > 0 && page > totalPages) {
      setPage(totalPages);
    }
  }, [page, totalPages]);

  useEffect(() => {
    if (isLoading || paginatedOrders.length === 0) {
      return;
    }

    if (selectedOrderId && paginatedOrders.some((order) => order.id === selectedOrderId)) {
      return;
    }

    setSelectedOrderId(paginatedOrders[0].id);
  }, [isLoading, paginatedOrders, selectedOrderId]);

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDirection(field === "updated_at" ? "desc" : "asc");
    }
    setPage(1);
  }

  function selectStatusFilter(status: OrderStatus | null) {
    setStatusFilter(status ? [status] : []);
    setPage(1);
  }

  async function handleRefresh() {
    if (!session) {
      return;
    }

    try {
      setIsRefreshing(true);
      setError(null);
      await refreshDashboardAndDetail(session.access_token, selectedOrderId);
    } catch (refreshError) {
      if (refreshError instanceof Error && isAuthFailureMessage(refreshError.message)) {
        handleAuthExpired(
          refreshError.message === FORBIDDEN_ERROR
            ? "Your access is no longer valid. Sign in again."
            : undefined
        );
        return;
      }

      if (!restoreCachedDashboard(selectedOrderId, "Live sync failed. Showing the last successful snapshot.")) {
        setError(
          refreshError instanceof Error
            ? refreshError.message
            : "Unknown error while refreshing dashboard data."
        );
      }
    } finally {
      setIsRefreshing(false);
    }
  }

  function persistCurrentConsoleSnapshot(
    nextOrders: OrderCard[],
    nextSelectedOrderId: string | null,
    nextSelectedOrderDetail: OrderDetail | null
  ) {
    if (!lifecycle) {
      return;
    }

    persistCachedConsoleSnapshot({
      dashboard: {
        orders: nextOrders,
        users,
        lifecycle
      },
      lastRefreshedAt: lastRefreshedAt ?? new Date().toISOString(),
      selectedOrderId: nextSelectedOrderId,
      selectedOrderDetail: nextSelectedOrderDetail
    });
  }

  function applyQueuedCreatePreview(mutation: QueuedCreateOrderMutation) {
    const preview = buildQueuedCreateOrderPreview(mutation);
    const nextOrders = [preview.order, ...orders];

    setOrders(nextOrders);
    setSelectedOrderId(preview.order.id);
    applyDetailState(preview.detail, null);
    persistCurrentConsoleSnapshot(nextOrders, preview.order.id, preview.detail);
  }

  function applyQueuedCommentPreview(mutation: QueuedCommentMutation) {
    const previewComment = buildQueuedCommentPreview(mutation);
    const nextUpdatedAt = mutation.createdAt;
    const nextOrders = orders.map((order) =>
      order.id === mutation.payload.orderId
        ? { ...order, updated_at: nextUpdatedAt }
        : order
    );

    setOrders(nextOrders);

    if (selectedOrderDetail?.id === mutation.payload.orderId) {
      const nextDetail: OrderDetail = {
        ...selectedOrderDetail,
        updated_at: nextUpdatedAt,
        comments: [...selectedOrderDetail.comments, previewComment]
      };
      applyDetailState(nextDetail, null);
      persistCurrentConsoleSnapshot(nextOrders, mutation.payload.orderId, nextDetail);
      return;
    }

    persistCurrentConsoleSnapshot(nextOrders, selectedOrderId, selectedOrderDetail);
  }

  function applyQueuedStatusPreview(mutation: QueuedStatusTransitionMutation) {
    const nextUpdatedAt = mutation.createdAt;
    const nextOrders = orders.map((order) =>
      order.id === mutation.payload.orderId
        ? { ...order, status: mutation.payload.toStatus, updated_at: nextUpdatedAt }
        : order
    );

    setOrders(nextOrders);

    if (selectedOrderDetail?.id === mutation.payload.orderId) {
      const nextDetail: OrderDetail = {
        ...selectedOrderDetail,
        status: mutation.payload.toStatus,
        updated_at: nextUpdatedAt,
        history: [
          ...selectedOrderDetail.history,
          buildQueuedStatusHistoryEvent(mutation, selectedOrderDetail.status)
        ]
      };
      applyDetailState(nextDetail, null);
      persistCurrentConsoleSnapshot(nextOrders, mutation.payload.orderId, nextDetail);
      return;
    }

    persistCurrentConsoleSnapshot(nextOrders, selectedOrderId, selectedOrderDetail);
  }

  function handleRecoverSelection() {
    if (!recoveryCandidateOrder) {
      handleClearSelection();
      return;
    }

    setSelectedOrderId(recoveryCandidateOrder.id);
    setSelectedOrderDetail(null);
    setDetailError(null);
    setCommentDraft("");
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      setIsAuthenticating(true);
      setAuthError(null);

      const nextSession = await login(authForm);

      persistSession(nextSession);
      setSession(nextSession);
    } catch (loginError) {
      setAuthError(
        loginError instanceof Error && isAuthFailureMessage(loginError.message)
          ? "Invalid email or password."
          : loginError instanceof Error
            ? loginError.message
            : "Sign-in failed."
      );
    } finally {
      setIsAuthenticating(false);
    }
  }

  function handleLogout() {
    clearCachedConsoleSnapshot();
    if (session) {
      clearQueuedMutations(session.user.id);
    }
    persistSession(null);
    setSession(null);
    setOrders([]);
    setUsers([]);
    setLifecycle(null);
    setSelectedOrderId(null);
  }

  async function handleRoleSignIn(role: "operator" | "customer") {
    const credentials = role === "operator"
      ? { email: "operator@example.com", password: "operator123" }
      : { email: "customer@example.com", password: "customer123" };
    setAuthForm({ email: credentials.email, password: credentials.password });

    try {
      setIsAuthenticating(true);
      setAuthError(null);

      const nextSession = await login(credentials);

      persistSession(nextSession);
      setSession(nextSession);
    } catch (loginError) {
      setAuthError(
        loginError instanceof Error && isAuthFailureMessage(loginError.message)
          ? "Invalid email or password."
          : loginError instanceof Error
            ? loginError.message
            : "Sign-in failed."
      );
    } finally {
      setIsAuthenticating(false);
    }
  }

  function handleClearSelection() {
    setSelectedOrderId(null);
    setSelectedOrderDetail(null);
    setDetailError(null);
    setCommentDraft("");
    setError(null);
    setActionError(null);
    setIsCreateOpen(false);
    setOpenActionsOrderId(null);
    setSearchQuery("");
    setPage(1);
    setIsRefreshing(false);
    setLastRefreshedAt(null);
    setSyncSource("live");
    setSyncNotice(null);
    setPendingMutations([]);
    setQueueNotice(null);
    setQueueError(null);
    setIsUserMenuOpen(false);
  }

  async function handleCreateOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!currentCustomer) {
      setActionError("Customer seed user is missing.");
      return;
    }

    if (!session) {
      setActionError("Sign in again to create orders.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await createOrder(session.access_token, {
        title: formState.title,
        description: formState.description,
        customer_id: currentCustomer.id
      });

      setFormState({ title: "", description: "" });
      setIsCreateOpen(false);
      await refreshDashboardAndDetail(session.access_token);
    } catch (submitError) {
      if (submitError instanceof Error && isOfflineQueueableError(submitError.message)) {
        const queuedMutation: QueuedCreateOrderMutation = {
          id: `queued-create-${Date.now()}`,
          createdAt: new Date().toISOString(),
          userId: session.user.id,
          type: "create_order",
          tempOrderId: `queued-order-${Date.now()}`,
          payload: {
            title: formState.title,
            description: formState.description,
            customerId: currentCustomer.id,
            customerName: currentCustomer.name,
            createdBy: session.user
          }
        };

        enqueueMutation(queuedMutation);
        applyQueuedCreatePreview(queuedMutation);
        setFormState({ title: "", description: "" });
        setIsCreateOpen(false);
        setQueueNotice(queueMutationNotice(pendingMutations.length + 1));
        setQueueError(getSyncErrorMessage(submitError.message));
        return;
      }

      if (submitError instanceof Error && isAuthFailureMessage(submitError.message)) {
        handleAuthExpired(
          submitError.message === FORBIDDEN_ERROR
            ? "Your access is no longer valid. Sign in again."
            : undefined
        );
        return;
      }

      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Order creation failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleTransition(orderId: string, toStatus: OrderStatus) {
    if (!isOperator) {
      setActionError("Only operators can change order status.");
      return;
    }

    if (!operator) {
      setActionError("Operator seed user is missing.");
      return;
    }

    if (!session) {
      setActionError("Sign in again to change status.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await transitionOrderStatus(session.access_token, orderId, {
        changed_by_id: operator.id,
        to_status: toStatus,
        reason: `Operator moved order to ${statusLabels[toStatus]}.`
      });

      setOpenActionsOrderId(null);
      await refreshDashboardAndDetail(session.access_token, orderId);
    } catch (submitError) {
      if (submitError instanceof Error && isOfflineQueueableError(submitError.message)) {
        const queuedMutation: QueuedStatusTransitionMutation = {
          id: `queued-status-${Date.now()}`,
          createdAt: new Date().toISOString(),
          userId: session.user.id,
          type: "transition_status",
          payload: {
            orderId,
            toStatus,
            reason: `Operator moved order to ${statusLabels[toStatus]}.`,
            changedBy: session.user
          }
        };

        enqueueMutation(queuedMutation);
        applyQueuedStatusPreview(queuedMutation);
        setOpenActionsOrderId(null);
        setQueueNotice(queueMutationNotice(pendingMutations.length + 1));
        setQueueError(getSyncErrorMessage(submitError.message));
        return;
      }

      if (submitError instanceof Error && isAuthFailureMessage(submitError.message)) {
        handleAuthExpired(
          submitError.message === FORBIDDEN_ERROR
            ? "Your access is no longer valid. Sign in again."
            : undefined
        );
        return;
      }

      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Status transition failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleAddComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!session) {
      setActionError("Sign in again to add comments.");
      return;
    }

    if (!isOperator) {
      setActionError("Only operators can leave queue comments.");
      return;
    }

    if (!selectedOrderId) {
      setActionError("Select an order before adding a comment.");
      return;
    }

    if (!commentDraft.trim()) {
      setActionError("Comment cannot be empty.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError(null);

      await addOrderComment(session.access_token, selectedOrderId, {
        author_id: session.user.id,
        body: commentDraft.trim()
      });

      setCommentDraft("");
      await refreshDashboardAndDetail(session.access_token, selectedOrderId);
    } catch (submitError) {
      if (submitError instanceof Error && isOfflineQueueableError(submitError.message)) {
        const queuedMutation: QueuedCommentMutation = {
          id: `queued-comment-${Date.now()}`,
          createdAt: new Date().toISOString(),
          userId: session.user.id,
          type: "add_comment",
          payload: {
            orderId: selectedOrderId,
            body: commentDraft.trim(),
            author: session.user
          }
        };

        enqueueMutation(queuedMutation);
        applyQueuedCommentPreview(queuedMutation);
        setCommentDraft("");
        setQueueNotice(queueMutationNotice(pendingMutations.length + 1));
        setQueueError(getSyncErrorMessage(submitError.message));
        return;
      }

      if (submitError instanceof Error && isAuthFailureMessage(submitError.message)) {
        handleAuthExpired(
          submitError.message === FORBIDDEN_ERROR
            ? "Your access is no longer valid. Sign in again."
            : undefined
        );
        return;
      }

      setActionError(
        submitError instanceof Error
          ? submitError.message
          : "Comment submission failed."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!session) {
    return (
      <ErrorBoundary>
        <AuthScreen
          email={authForm.email}
          password={authForm.password}
          isAuthenticating={isAuthenticating}
          authError={authError}
          onEmailChange={(email) => setAuthForm((current) => ({ ...current, email }))}
          onPasswordChange={(password) => setAuthForm((current) => ({ ...current, password }))}
          onSubmit={handleLogin}
          onRoleSignIn={handleRoleSignIn}
        />
      </ErrorBoundary>
    );
  }

  return (
    <ErrorBoundary>
      <main className="shell">
        <Hero
          session={session}
          orderCount={orders.length}
          isLoading={isLoading}
          userInitials={userInitials}
          isUserMenuOpen={isUserMenuOpen}
          onUserMenuToggle={() => setIsUserMenuOpen((current) => !current)}
          onLogout={handleLogout}
          userRole={session.user.role}
        />

        <div className="console-heading">
          <h1>{consoleTitle}</h1>
        </div>

        <section className="panel panel-console">
          <StatusSummary
            activeStatusFilter={statusFilter.length === 1 ? statusFilter[0] : null}
            groupedStatuses={groupedStatuses}
            totalCount={orders.length}
            onStatusFilterChange={selectStatusFilter}
          />

          <div className="console-grid">
            <OrderTable
              orders={orders}
              paginatedOrders={paginatedOrders}
              sortedOrders={sortedOrders}
              lifecycle={lifecycle}
              isLoading={isLoading}
              isRefreshing={isRefreshing}
              isSubmitting={isSubmitting}
              isOperator={isOperator}
              isCreateOpen={isCreateOpen}
              searchQuery={searchQuery}
              page={page}
              pageSize={pageSize}
              totalPages={totalPages}
              actionError={actionError}
              currentCustomer={currentCustomer}
              selectedOrderId={selectedOrderId}
              openActionsOrderId={openActionsOrderId}
              sortField={sortField}
              sortDirection={sortDirection}
              syncSource={syncSource}
              syncNotice={syncNotice}
              lastRefreshedAt={lastRefreshedAt}
              pendingMutationCount={pendingMutationCount}
              queueNotice={queueNotice}
              queueError={queueError}
              error={error}
              formState={formState}
              onSearchChange={setSearchQuery}
              onRefresh={handleRefresh}
              onToggleCreateOpen={() => setIsCreateOpen((current) => !current)}
              onCreateOrder={handleCreateOrder}
              onFormTitleChange={(title) => setFormState((current) => ({ ...current, title }))}
              onFormDescriptionChange={(description) => setFormState((current) => ({ ...current, description }))}
              onToggleSort={toggleSort}
              onSelectOrder={setSelectedOrderId}
              onToggleActionsOrderId={setOpenActionsOrderId}
              onTransition={handleTransition}
              onPageChange={setPage}
              onPageSizeChange={setPageSize}
            />

            <OrderInspector
              selectedOrderDetail={selectedOrderDetail}
              detailError={detailError}
              isDetailLoading={isDetailLoading}
              isRefreshing={isRefreshing}
              isSubmitting={isSubmitting}
              isOperator={isOperator}
              selectedOrderIsQueuedDraft={selectedOrderIsQueuedDraft}
              commentDraft={commentDraft}
              recoveryCandidateOrder={recoveryCandidateOrder}
              onRefresh={handleRefresh}
              onClearSelection={handleClearSelection}
              onRecoverSelection={handleRecoverSelection}
              onCommentDraftChange={setCommentDraft}
              onAddComment={handleAddComment}
            />
          </div>
        </section>
      </main>
    </ErrorBoundary>
  );
}
