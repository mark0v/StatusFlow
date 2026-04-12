import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import statusFlowLogo from "./assets/statusflow-logo.svg";
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
  formatTimestamp,
  historySummary,
  orderedStatuses,
  statusLabels,
  statusTone,
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
  const [isStatusFilterOpen, setIsStatusFilterOpen] = useState(false);
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

  // Derive initials from user name for avatar
  const userInitials = useMemo(() => {
    if (!session?.user?.name) return "?";
    const parts = session.user.name.trim().split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    return session.user.name.slice(0, 2).toUpperCase();
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

      if (!target.closest(".column-filter-wrap")) {
        setIsStatusFilterOpen(false);
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

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDirection(field === "updated_at" ? "desc" : "asc");
    }
    setPage(1);
  }

  function toggleStatusFilter(status: OrderStatus) {
    setStatusFilter((current) =>
      current.includes(status)
        ? current.filter((entry) => entry !== status)
        : [...current, status]
    );
    setPage(1);
  }

  function sortIndicator(field: SortField) {
    if (sortField !== field) {
      return "^";
    }

    return sortDirection === "asc" ? "^" : "v";
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
        ? {
            ...order,
            updated_at: nextUpdatedAt
          }
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
        ? {
            ...order,
            status: mutation.payload.toStatus,
            updated_at: nextUpdatedAt
          }
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

  function handleClearSelection() {
    setSelectedOrderId(null);
    setSelectedOrderDetail(null);
    setDetailError(null);
    setCommentDraft("");
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
      <main className="shell shell-auth">
        <section className="auth-panel">
          <img
            alt="StatusFlow operator console"
            className="hero-logo auth-logo"
            src={statusFlowLogo}
          />
          <p className="eyebrow">Live sign in</p>
          <h1>Access the live workflow console</h1>
          <p className="lead">
            Sign in with a seeded operator or customer account to enter the
            shared workflow used across web and mobile.
          </p>

          <form className="auth-form" onSubmit={handleLogin}>
            <label className="field">
              <span>Email</span>
              <input
                autoComplete="username"
                value={authForm.email}
                onChange={(event) =>
                  setAuthForm((current) => ({ ...current, email: event.target.value }))
                }
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                autoComplete="current-password"
                type="password"
                value={authForm.password}
                onChange={(event) =>
                  setAuthForm((current) => ({ ...current, password: event.target.value }))
                }
              />
            </label>
            <button className="primary-action" disabled={isAuthenticating} type="submit">
              {isAuthenticating ? "Signing in..." : "Sign in"}
            </button>
          </form>

          {authError ? (
            <div className="feedback-card feedback-error compact">
              <span className="feedback-eyebrow">Auth</span>
              <strong>Unable to sign in.</strong>
              <p>{authError}</p>
            </div>
          ) : null}

          <div className="auth-hint">
            <strong>Seed operator</strong>
            <span>operator@example.com / operator123</span>
          </div>
          <div className="auth-hint">
            <strong>Seed customer</strong>
            <span>customer@example.com / customer123</span>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <section className="hero">
        <div className="hero-top">
          <div className="hero-brand">
            <img
              alt="StatusFlow operator console"
              className="hero-logo"
              src={statusFlowLogo}
            />
          </div>
          <div className="hero-actions">
            <span className="hero-order-count">
              {isLoading ? "…" : `${orders.length} orders`}
            </span>
            <div className="user-menu-wrap">
              <button
                className="user-avatar-btn"
                onClick={() => setIsUserMenuOpen((current) => !current)}
                aria-label="User menu"
                aria-expanded={isUserMenuOpen}
                type="button"
              >
                <span className="user-avatar-initials">{userInitials}</span>
              </button>
              {isUserMenuOpen ? (
                <div className="user-dropdown">
                  <div className="user-dropdown-info">
                    <span className="user-dropdown-name">{session.user.name}</span>
                    <span className="user-dropdown-role">{session.user.role}</span>
                  </div>
                  <hr className="user-dropdown-sep" />
                  <a
                    className="user-dropdown-item"
                    href={`${API_BASE_URL}/docs`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    API docs
                  </a>
                  <div className="user-dropdown-item user-dropdown-endpoint">
                    <span className="user-dropdown-endpoint-label">Endpoint</span>
                    <span className="user-dropdown-endpoint-value">{API_BASE_URL}</span>
                  </div>
                  <hr className="user-dropdown-sep" />
                  <button
                    className="user-dropdown-item user-dropdown-item-danger"
                    onClick={() => {
                      handleLogout();
                      setIsUserMenuOpen(false);
                    }}
                    type="button"
                  >
                    Sign out
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      <section className="panel panel-console">
        <div className="panel-header">
          <div>
            <h2>Operate the live workflow</h2>
          </div>
        </div>

        <div className="summary-strip">
          {groupedStatuses.map(([status, count]) => (
            <article className="status-card" key={status}>
              <span>{statusLabels[status]}</span>
              <strong>{count}</strong>
            </article>
          ))}
        </div>

        <section className="table-stage">
          <div className="table-toolbar">
            <div>
              <h3>Review and move orders forward</h3>
            </div>
          </div>

          <div className="queue-controls">
            <label className="field queue-search-field">
              <input
                aria-label="Search queue"
                onChange={(event) => {
                  setSearchQuery(event.target.value);
                  setPage(1);
                }}
                placeholder="Search code, title, or customer"
                value={searchQuery}
              />
            </label>

            <div className="toolbar-actions">
              <button
                className="secondary-action"
                disabled={isLoading || isRefreshing}
                onClick={() => void handleRefresh()}
                type="button"
              >
                {isRefreshing ? "Refreshing..." : "Refresh"}
              </button>
              <button
                className="primary-action"
                onClick={() => setIsCreateOpen((current) => !current)}
                type="button"
              >
                {isCreateOpen ? "Close create form" : "Create order"}
              </button>
            </div>
          </div>

          {isCreateOpen ? (
            <article className="create-reveal">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Create order</p>
                  <h3>Add a new order to the live queue</h3>
                </div>
              </div>

              <form className="create-form create-form-inline" onSubmit={handleCreateOrder}>
                <label className="field">
                  <span>Order title</span>
                  <input
                    required
                    minLength={3}
                    value={formState.title}
                    onChange={(event) =>
                      setFormState((current) => ({ ...current, title: event.target.value }))
                    }
                    placeholder="Inspect delivery damage"
                  />
                </label>

                <label className="field field-wide">
                  <span>Description</span>
                  <textarea
                    value={formState.description}
                    onChange={(event) =>
                      setFormState((current) => ({
                        ...current,
                        description: event.target.value
                      }))
                    }
                    placeholder="Add context that web and mobile operators should both see."
                    rows={3}
                  />
                </label>

                <button
                  className="primary-action"
                  disabled={isSubmitting || !currentCustomer}
                  type="submit"
                >
                  {isSubmitting ? "Submitting..." : "Create order"}
                </button>
              </form>
            </article>
          ) : null}

          {actionError ? (
            <div className="feedback-card feedback-error compact">
              <span className="feedback-eyebrow">Action blocked</span>
              <strong>Action failed.</strong>
              <p>{actionError}</p>
            </div>
          ) : null}

          {isLoading ? (
            <div className="feedback-card feedback-sync">
              <span className="feedback-eyebrow">Live sync</span>
              <strong>Loading orders from the API</strong>
              <p>Refreshing the queue snapshot and row actions.</p>
            </div>
          ) : null}

          {syncSource === "cached" && syncNotice ? (
            <div className="feedback-card feedback-sync feedback-cached">
              <span className="feedback-eyebrow">Cached snapshot</span>
              <strong>Showing the last successful queue state.</strong>
              <p>{syncNotice}</p>
            </div>
          ) : null}

          {pendingMutationCount > 0 || queueNotice || queueError ? (
            <div className={`feedback-card compact ${queueError ? "feedback-error" : "feedback-sync"}`}>
              <span className="feedback-eyebrow">Offline queue</span>
              <strong>
                {pendingMutationCount > 0
                  ? `${pendingMutationCount} queued change${pendingMutationCount === 1 ? "" : "s"} waiting to sync.`
                  : "Queued changes synced."}
              </strong>
              <p>{queueError ?? queueNotice ?? "Queued changes will replay on the next successful refresh."}</p>
            </div>
          ) : null}

          {error && syncSource !== "cached" ? (
            <div className="feedback-card feedback-error">
              <span className="feedback-eyebrow">Error</span>
              <strong>Dashboard failed to load.</strong>
              <p>{error}</p>
            </div>
          ) : null}

          {!isLoading && !error ? (
            <>
              <div className="table-wrap">
                <table className="orders-table">
                <colgroup>
                  <col className="col-code" />
                  <col className="col-title" />
                  <col className="col-customer" />
                  <col className="col-status" />
                  <col className="col-updated" />
                  <col className="col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Title</th>
                    <th>
                      <button
                        className="column-sort"
                        onClick={() => toggleSort("customer_name")}
                        type="button"
                      >
                        Customer <span>{sortIndicator("customer_name")}</span>
                      </button>
                    </th>
                    <th>
                      <div className="column-filter-wrap">
                        <button
                          className="column-sort"
                          onClick={() => toggleSort("status")}
                          type="button"
                        >
                          Status <span>{sortIndicator("status")}</span>
                        </button>
                        <button
                          className={`filter-trigger ${statusFilter.length > 0 ? "active" : ""}`}
                          onClick={() => setIsStatusFilterOpen((current) => !current)}
                          type="button"
                        >
                          Filter
                        </button>
                        {isStatusFilterOpen ? (
                          <div className="filter-menu">
                            {orderedStatuses.map((status) => (
                              <label className="filter-option" key={status}>
                                <input
                                  checked={statusFilter.includes(status)}
                                  onChange={() => toggleStatusFilter(status)}
                                  type="checkbox"
                                />
                                <span>{statusLabels[status]}</span>
                              </label>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </th>
                    <th>
                      <button
                        className="column-sort"
                        onClick={() => toggleSort("updated_at")}
                        type="button"
                      >
                        Updated <span>{sortIndicator("updated_at")}</span>
                      </button>
                    </th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedOrders.length === 0 ? (
                    <tr>
                      <td className="empty-row" colSpan={6}>
                        <span className="empty-row-eyebrow">No matching orders</span>
                        <strong>Nothing matches the current queue controls.</strong>
                        <p>Clear the search or active status filters to bring orders back into view.</p>
                      </td>
                    </tr>
                  ) : (
                    paginatedOrders.map((order) => {
                      const nextStatuses = lifecycle?.allowed_transitions[order.status] ?? [];

                      return (
                        <tr
                          className={selectedOrderId === order.id ? "is-selected" : ""}
                          key={order.id}
                          onClick={() => setSelectedOrderId(order.id)}
                        >
                          <td className="cell-code">{order.code}</td>
                          <td className="cell-title">{order.title}</td>
                          <td className="cell-customer">{order.customer_name}</td>
                          <td>
                            <span className={`pill ${statusTone(order.status)}`}>
                              {statusLabels[order.status]}
                            </span>
                          </td>
                          <td className="cell-updated">{formatTimestamp(order.updated_at)}</td>
                          <td className="cell-actions">
                            <div className="table-actions">
                              {nextStatuses.length === 0 ? (
                                <span className="transition-terminal">Terminal state</span>
                              ) : order.id.startsWith("queued-order-") ? (
                                <span className="transition-terminal">Queued offline</span>
                              ) : !isOperator ? (
                                <span className="transition-terminal">Operator only</span>
                              ) : (
                                <div className="row-action-menu">
                                  <button
                                    className="transition-button transition-trigger"
                                    onClick={() =>
                                      setOpenActionsOrderId((current) =>
                                        current === order.id ? null : order.id
                                      )
                                    }
                                    type="button"
                                  >
                                    Change status
                                  </button>
                                  {openActionsOrderId === order.id ? (
                                    <div className="row-dropdown">
                                      {nextStatuses.map((status) => (
                                        <button
                                          key={status}
                                          className="dropdown-action"
                                          disabled={isSubmitting}
                                          onClick={() => void handleTransition(order.id, status)}
                                          type="button"
                                        >
                                          {statusLabels[status]}
                                        </button>
                                      ))}
                                    </div>
                                  ) : null}
                                </div>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            {sortedOrders.length > 0 ? (
              <div className="pagination-bar">
                <div className="pagination-info">
                  <span>
                    Showing {(page - 1) * pageSize + 1}–
                    {Math.min(page * pageSize, sortedOrders.length)} of {sortedOrders.length} orders
                  </span>
                  <label className="page-size-selector">
                    <span>Per page:</span>
                    <select
                      value={pageSize}
                      onChange={(event) => {
                        setPageSize(Number(event.target.value));
                        setPage(1);
                      }}
                    >
                      {[10, 25, 50, 100, 250].map((size) => (
                        <option key={size} value={size}>
                          {size}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                {totalPages > 1 ? (
                  <div className="pagination-controls">
                    <button
                      className="pagination-arrow"
                      disabled={page === 1}
                      onClick={() => setPage((current) => Math.max(1, current - 1))}
                      type="button"
                      aria-label="Previous page"
                    >
                      ‹
                    </button>
                    <span className="pagination-pages">
                      {(() => {
                        const pages: (number | string)[] = [];
                        const showPages = new Set<number>();

                        // Always show current page
                        showPages.add(page);

                        // Show up to 2 neighbors
                        for (let offset = -2; offset <= 2; offset++) {
                          const p = page + offset;
                          if (p >= 1 && p <= totalPages) {
                            showPages.add(p);
                          }
                        }

                        // Limit to max 5 pages total (current + 2 each side)
                        const sorted = Array.from(showPages).sort((a, b) => a - b);
                        if (sorted.length > 5) {
                          const startIdx = Math.max(0, sorted.indexOf(page) - 2);
                          const selected = sorted.slice(startIdx, startIdx + 5);
                          selected.forEach((p) => pages.push(p));
                        } else {
                          sorted.forEach((p) => pages.push(p));
                        }

                        // Add ellipsis where needed
                        const result: (number | string)[] = [];
                        pages.forEach((p, index) => {
                          if (index > 0 && typeof p === "number" && typeof pages[index - 1] === "number") {
                            if (p - (pages[index - 1] as number) > 1) {
                              result.push("…");
                            }
                          }

                          result.push(p);
                        });

                        return result.map((item, index) =>
                          typeof item === "string" ? (
                            <span className="pagination-ellipsis" key={`ellipsis-${index}`}>
                              {item}
                            </span>
                          ) : (
                            <button
                              className={`pagination-page-btn${item === page ? " active" : ""}`}
                              key={item}
                              onClick={() => setPage(item)}
                              type="button"
                            >
                              {item}
                            </button>
                          )
                        );
                      })()}
                    </span>
                    <button
                      className="pagination-arrow"
                      disabled={page === totalPages}
                      onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                      type="button"
                      aria-label="Next page"
                    >
                      ›
                    </button>
                  </div>
                ) : null}
              </div>
            ) : null}
            </>
          ) : null}

          <section className="detail-inspector">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Order inspector</p>
                <h3>Comments and workflow history</h3>
              </div>
            </div>

            {isDetailLoading ? (
              <div className="feedback-card feedback-sync">
                <span className="feedback-eyebrow">Inspector</span>
                <strong>Loading order detail</strong>
                <p>Fetching comments, history, and the latest description.</p>
              </div>
            ) : null}

            {!isDetailLoading && detailError ? (
              <article className="inspector-card detail-recovery-card">
                <span className="feedback-eyebrow">Detail state</span>
                <strong>Selected order is unavailable.</strong>
                <p>
                  {detailError}
                  {" "}
                  Refresh the queue or pick another order to keep working.
                </p>
                <div className="detail-recovery-actions">
                  <button
                    className="secondary-action"
                    disabled={isRefreshing}
                    onClick={() => void handleRefresh()}
                    type="button"
                  >
                    {isRefreshing ? "Refreshing..." : "Refresh"}
                  </button>
                  <button
                    className="secondary-action"
                    onClick={handleClearSelection}
                    type="button"
                  >
                    Clear selection
                  </button>
                  {recoveryCandidateOrder ? (
                    <button
                      className="primary-action"
                      onClick={handleRecoverSelection}
                      type="button"
                    >
                      Open {recoveryCandidateOrder.code}
                    </button>
                  ) : null}
                </div>
              </article>
            ) : null}

            {!isDetailLoading && !detailError && selectedOrderDetail ? (
              <div className="inspector-grid">
                <article className="inspector-card inspector-summary">
                  <div className="inspector-topline">
                    <span className="cell-code">{selectedOrderDetail.code}</span>
                    <span className={`pill ${statusTone(selectedOrderDetail.status)}`}>
                      {statusLabels[selectedOrderDetail.status]}
                    </span>
                  </div>
                  <h4>{selectedOrderDetail.title}</h4>
                  <p>{selectedOrderDetail.description || "No description provided yet."}</p>

                  <div className="inspector-meta">
                    <div>
                      <span>Customer</span>
                      <strong>{selectedOrderDetail.customer_name}</strong>
                    </div>
                    <div>
                      <span>Updated</span>
                      <strong>{formatTimestamp(selectedOrderDetail.updated_at)}</strong>
                    </div>
                  </div>
                </article>

                <article className="inspector-card">
                  <div className="inspector-section-heading">
                    <div>
                      <p className="eyebrow">History</p>
                      <h4>Recent status movement</h4>
                    </div>
                    <span className="inspector-count">{selectedOrderDetail.history.length}</span>
                  </div>

                  {selectedOrderDetail.history.length === 0 ? (
                    <div className="mini-empty">No workflow events recorded yet.</div>
                  ) : (
                    <div className="timeline-list">
                      {[...selectedOrderDetail.history].reverse().map((event) => (
                        <article className="timeline-item" key={event.id}>
                          <strong>{historySummary(event)}</strong>
                          <span>
                            {event.changed_by.name} | {formatTimestamp(event.changed_at)}
                          </span>
                          <p>{event.reason}</p>
                        </article>
                      ))}
                    </div>
                  )}
                </article>

                <article className="inspector-card">
                  <div className="inspector-section-heading">
                    <div>
                      <p className="eyebrow">Comments</p>
                      <h4>{isOperator ? "Operator notes" : "Queue notes"}</h4>
                    </div>
                    <span className="inspector-count">{selectedOrderDetail.comments.length}</span>
                  </div>

                  {isOperator && !selectedOrderIsQueuedDraft ? (
                    <form className="comment-form" onSubmit={handleAddComment}>
                      <label className="field field-wide">
                        <span>Add comment</span>
                        <textarea
                          value={commentDraft}
                          onChange={(event) => setCommentDraft(event.target.value)}
                          placeholder="Leave a note for the next operator."
                          rows={3}
                        />
                      </label>
                      <button
                        className="primary-action"
                        disabled={isSubmitting || commentDraft.trim().length === 0}
                        type="submit"
                      >
                        {isSubmitting ? "Posting..." : "Post comment"}
                      </button>
                    </form>
                  ) : isOperator ? (
                    <div className="mini-empty">
                      Sync queued draft orders before adding comments.
                    </div>
                  ) : (
                    <div className="mini-empty">
                      Comments are available in operator mode.
                    </div>
                  )}

                  {selectedOrderDetail.comments.length === 0 ? (
                    <div className="mini-empty">No comments yet for this order.</div>
                  ) : (
                    <div className="timeline-list">
                      {[...selectedOrderDetail.comments].reverse().map((comment) => (
                        <article className="timeline-item" key={comment.id}>
                          <strong>{comment.author.name}</strong>
                          <span>{formatTimestamp(comment.created_at)}</span>
                          <p>{comment.body}</p>
                        </article>
                      ))}
                    </div>
                  )}
                </article>
              </div>
            ) : null}

            {!isDetailLoading && !detailError && !selectedOrderDetail ? (
              <div className="feedback-card feedback-sync">
                <span className="feedback-eyebrow">Inspector</span>
                <strong>Select an order to inspect.</strong>
                <p>Choose any row to review comments, history, and description.</p>
              </div>
            ) : null}
          </section>
        </section>
      </section>
    </main>
  );
}
