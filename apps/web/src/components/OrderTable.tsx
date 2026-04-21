import {
  formatTimestamp,
  statusLabels,
  statusTone,
  type OrderCard,
  type OrderStatus,
  type OrderStatusLifecycle,
  type SortDirection,
  type SortField
} from "../data/webTypes";

interface Props {
  orders: OrderCard[];
  paginatedOrders: OrderCard[];
  sortedOrders: OrderCard[];
  lifecycle: OrderStatusLifecycle | null;
  isLoading: boolean;
  isRefreshing: boolean;
  isSubmitting: boolean;
  isOperator: boolean;
  isCreateOpen: boolean;
  searchQuery: string;
  page: number;
  pageSize: number;
  totalPages: number;
  actionError: string | null;
  currentCustomer: { id: string; name: string } | null;
  selectedOrderId: string | null;
  openActionsOrderId: string | null;
  sortField: SortField;
  sortDirection: SortDirection;
  syncSource: "live" | "cached";
  syncNotice: string | null;
  lastRefreshedAt: string | null;
  pendingMutationCount: number;
  queueNotice: string | null;
  queueError: string | null;
  error: string | null;
  formState: { title: string; description: string };
  onSearchChange: (query: string) => void;
  onRefresh: () => void;
  onToggleCreateOpen: () => void;
  onCreateOrder: (event: React.FormEvent<HTMLFormElement>) => void;
  onFormTitleChange: (title: string) => void;
  onFormDescriptionChange: (description: string) => void;
  onToggleSort: (field: SortField) => void;
  onSelectOrder: (orderId: string) => void;
  onToggleActionsOrderId: (orderId: string | null) => void;
  onTransition: (orderId: string, toStatus: OrderStatus) => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}

function sortIndicator(field: SortField, sortField: SortField, sortDirection: SortDirection) {
  if (sortField !== field) {
    return "↕";
  }
  return sortDirection === "asc" ? " ↑" : " ↓";
}

function formatSyncLabel(lastRefreshedAt: string | null, isRefreshing: boolean) {
  if (isRefreshing) {
    return "Syncing";
  }

  if (!lastRefreshedAt) {
    return "Sync pending";
  }

  return `Sync ${new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(lastRefreshedAt))}`;
}

export function OrderTable({
  orders,
  paginatedOrders,
  sortedOrders,
  lifecycle,
  isLoading,
  isRefreshing,
  isSubmitting,
  isOperator,
  isCreateOpen,
  searchQuery,
  page,
  pageSize,
  totalPages,
  actionError,
  currentCustomer,
  selectedOrderId,
  openActionsOrderId,
  sortField,
  sortDirection,
  syncSource,
  syncNotice,
  lastRefreshedAt,
  pendingMutationCount,
  queueNotice,
  queueError,
  error,
  formState,
  onSearchChange,
  onRefresh,
  onToggleCreateOpen,
  onCreateOrder,
  onFormTitleChange,
  onFormDescriptionChange,
  onToggleSort,
  onSelectOrder,
  onToggleActionsOrderId,
  onTransition,
  onPageChange,
  onPageSizeChange
}: Props) {
  return (
    <section className="table-stage">
      <div className="queue-controls">
        <label className="field queue-search-field">
          <input
            aria-label="Search queue"
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Search code, title, or customer"
            value={searchQuery}
          />
        </label>

        <div className="toolbar-actions">
          <div className={`sync-pill ${syncSource === "cached" ? "cached" : ""}`}>
            <button
              aria-label="Refresh orders"
              className="refresh-icon-button"
              disabled={isLoading || isRefreshing}
              onClick={() => void onRefresh()}
              type="button"
            >
              ↻
            </button>
            <span>{formatSyncLabel(lastRefreshedAt, isRefreshing)}</span>
          </div>
          <button
            className="primary-action"
            onClick={onToggleCreateOpen}
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

          <form className="create-form create-form-inline" onSubmit={onCreateOrder}>
            <label className="field">
              <span>Order title</span>
              <input
                required
                minLength={3}
                value={formState.title}
                onChange={(event) => onFormTitleChange(event.target.value)}
                placeholder="Inspect delivery damage"
              />
            </label>

            <label className="field field-wide">
              <span>Description</span>
              <textarea
                value={formState.description}
                onChange={(event) => onFormDescriptionChange(event.target.value)}
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
                    onClick={() => onToggleSort("customer_name")}
                    type="button"
                  >
                    Customer <span className="sort-icon">{sortIndicator("customer_name", sortField, sortDirection)}</span>
                  </button>
                </th>
                <th>
                  <button
                    className="column-sort"
                    onClick={() => onToggleSort("status")}
                    type="button"
                  >
                    Status <span className="sort-icon">{sortIndicator("status", sortField, sortDirection)}</span>
                  </button>
                </th>
                <th>
                  <button
                    className="column-sort"
                    onClick={() => onToggleSort("updated_at")}
                    type="button"
                  >
                    Updated <span className="sort-icon">{sortIndicator("updated_at", sortField, sortDirection)}</span>
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
                      onClick={() => onSelectOrder(order.id)}
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
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onToggleActionsOrderId(openActionsOrderId === order.id ? null : order.id);
                                }}
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
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        void onTransition(order.id, status);
                                      }}
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
              <label className="page-size-selector">
                <span>Rows per page</span>
                <select
                  value={pageSize}
                  onChange={(event) => {
                    onPageSizeChange(Number(event.target.value));
                    onPageChange(1);
                  }}
                >
                  {[10, 25, 50, 100, 250].map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </label>
              <span>
                Showing {(page - 1) * pageSize + 1}–
                {Math.min(page * pageSize, sortedOrders.length)} of {sortedOrders.length} orders
              </span>
            </div>
            {totalPages > 1 ? (
              <div className="pagination-controls">
                <button
                  className="pagination-arrow"
                  disabled={page === 1}
                  onClick={() => onPageChange(Math.max(1, page - 1))}
                  type="button"
                  aria-label="Previous page"
                >
                  ‹
                </button>
                <span className="pagination-pages">
                  {(() => {
                    const pages: number[] = [];
                    for (let offset = -2; offset <= 2; offset++) {
                      const p = page + offset;
                      if (p >= 1 && p <= totalPages) {
                        pages.push(p);
                      }
                    }

                    const result: (number | string)[] = [];
                    pages.forEach((p, index) => {
                      if (index > 0 && p - pages[index - 1] > 1) {
                        result.push("…");
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
                          onClick={() => onPageChange(item)}
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
                  onClick={() => onPageChange(Math.min(totalPages, page + 1))}
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
    </section>
  );
}
