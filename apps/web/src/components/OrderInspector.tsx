import { useState, type FormEvent } from "react";
import {
  formatTimestamp,
  historySummary,
  statusLabels,
  statusTone,
  type OrderCard,
  type OrderDetail,
  type OrderStatus,
  type OrderStatusLifecycle
} from "../data/webTypes";

interface Props {
  selectedOrderDetail: OrderDetail | null;
  detailError: string | null;
  isDetailLoading: boolean;
  isRefreshing: boolean;
  isSubmitting: boolean;
  isOperator: boolean;
  lifecycle: OrderStatusLifecycle | null;
  openActionsOrderId: string | null;
  selectedOrderIsQueuedDraft: boolean;
  commentDraft: string;
  recoveryCandidateOrder: OrderCard | null;
  onRefresh: () => void;
  onClearSelection: () => void;
  onRecoverSelection: () => void;
  onToggleActionsOrderId: (orderId: string | null) => void;
  onTransition: (orderId: string, toStatus: OrderStatus) => void;
  onCommentDraftChange: (draft: string) => void;
  onAddComment: (event: FormEvent<HTMLFormElement>) => void;
}

type InspectorTab = "overview" | "history" | "comments";

export function OrderInspector({
  selectedOrderDetail,
  detailError,
  isDetailLoading,
  isRefreshing,
  isSubmitting,
  isOperator,
  lifecycle,
  openActionsOrderId,
  selectedOrderIsQueuedDraft,
  commentDraft,
  recoveryCandidateOrder,
  onRefresh,
  onClearSelection,
  onRecoverSelection,
  onToggleActionsOrderId,
  onTransition,
  onCommentDraftChange,
  onAddComment
}: Props) {
  const [activeTab, setActiveTab] = useState<InspectorTab>("overview");
  const visibleComments = isOperator ? selectedOrderDetail?.comments ?? [] : [];
  const nextStatuses = selectedOrderDetail
    ? lifecycle?.allowed_transitions[selectedOrderDetail.status] ?? []
    : [];
  const canChangeStatus = Boolean(
    isOperator &&
    selectedOrderDetail &&
    !selectedOrderDetail.id.startsWith("queued-order-") &&
    nextStatuses.length > 0
  );
  const isStatusMenuOpen = Boolean(
    selectedOrderDetail && openActionsOrderId === selectedOrderDetail.id
  );
  const commentTabLabel = isOperator ? "Comments" : "Messages";
  const commentCount = isOperator ? visibleComments.length : 0;
  const supportHref = selectedOrderDetail
    ? `mailto:support@statusflow.local?subject=${encodeURIComponent(`Order ${selectedOrderDetail.code}`)}`
    : "mailto:support@statusflow.local";

  return (
    <section className="detail-inspector">
      {!isDetailLoading && !detailError && !selectedOrderDetail && (
        <div className="inspector-empty">
          <p>Select an order to see its details.</p>
        </div>
      )}

      {isDetailLoading && (
        <div className="feedback-card feedback-sync">
          <span className="feedback-eyebrow">Loading</span>
          <strong>Fetching order detail</strong>
        </div>
      )}

      {!isDetailLoading && detailError && (
        <article className="inspector-card detail-recovery-card">
          <span className="feedback-eyebrow">Order not found</span>
          <strong>This order may have been removed.</strong>
          <p>Refresh the queue or pick another order.</p>
          <div className="detail-recovery-actions">
            <button
              className="secondary-action"
              disabled={isRefreshing}
              onClick={() => void onRefresh()}
              type="button"
            >
              {isRefreshing ? "Refreshing..." : "Refresh"}
            </button>
            <button
              className="secondary-action"
              onClick={onClearSelection}
              type="button"
            >
              Clear
            </button>
            {recoveryCandidateOrder && (
              <button
                className="primary-action"
                onClick={onRecoverSelection}
                type="button"
              >
                Open {recoveryCandidateOrder.code}
              </button>
            )}
          </div>
        </article>
      )}

      {!isDetailLoading && !detailError && selectedOrderDetail && (
        <article className="inspector-card inspector-summary order-detail-card">
          <div className="order-detail-card-head">
            <div className="inspector-topline">
              <span className="cell-code">{selectedOrderDetail.code}</span>
              <span className={`pill ${statusTone(selectedOrderDetail.status)}`}>
                {statusLabels[selectedOrderDetail.status]}
              </span>
            </div>
            <h4>{selectedOrderDetail.title}</h4>
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
            {isOperator && (
              <div className="inspector-actions">
                {canChangeStatus ? (
                  <div className="row-action-menu">
                    <button
                      className="primary-action inspector-primary-action"
                      onClick={() => onToggleActionsOrderId(isStatusMenuOpen ? null : selectedOrderDetail.id)}
                      type="button"
                    >
                      Change status
                    </button>
                    {isStatusMenuOpen ? (
                      <div className="row-dropdown inspector-dropdown">
                        {nextStatuses.map((status) => (
                          <button
                            key={status}
                            className="dropdown-action"
                            disabled={isSubmitting}
                            onClick={() => {
                              onToggleActionsOrderId(null);
                              void onTransition(selectedOrderDetail.id, status);
                            }}
                            type="button"
                          >
                            {statusLabels[status]}
                          </button>
                        ))}
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <span className="transition-terminal">
                    {selectedOrderDetail.id.startsWith("queued-order-")
                      ? "Queued offline"
                      : "Terminal state"}
                  </span>
                )}
              </div>
            )}
          </div>

          <div className="inspector-tabs" role="tablist" aria-label="Order detail sections">
            <button
              aria-selected={activeTab === "overview"}
              className={`inspector-tab ${activeTab === "overview" ? "active" : ""}`}
              onClick={() => setActiveTab("overview")}
              role="tab"
              type="button"
            >
              Overview
            </button>
            <button
              aria-selected={activeTab === "history"}
              className={`inspector-tab ${activeTab === "history" ? "active" : ""}`}
              onClick={() => setActiveTab("history")}
              role="tab"
              type="button"
            >
              History <span className="inspector-count">{selectedOrderDetail.history.length}</span>
            </button>
            <button
              aria-selected={activeTab === "comments"}
              className={`inspector-tab ${activeTab === "comments" ? "active" : ""}`}
              onClick={() => setActiveTab("comments")}
              role="tab"
              type="button"
            >
              {commentTabLabel} <span className="inspector-count">{commentCount}</span>
            </button>
          </div>

          <div className="inspector-tab-panel">
            {activeTab === "overview" && (
              <div className="detail-field-list">
                <div className="detail-field-card">
                  <span>Customer</span>
                  <strong>{selectedOrderDetail.customer_name}</strong>
                </div>
                <div className="detail-field-card detail-field-wide">
                  <span>Description</span>
                  <p>{selectedOrderDetail.description || "No description yet."}</p>
                </div>
                {!isOperator && (
                  <div className="detail-field-card detail-field-wide">
                    <span>Need help?</span>
                    <p>Contact support if this request needs a correction or a faster update.</p>
                    <a className="secondary-action customer-support-action" href={supportHref}>
                      Contact support
                    </a>
                  </div>
                )}
              </div>
            )}

            {activeTab === "history" && (
              <>
                {selectedOrderDetail.history.length === 0 ? (
                  <div className="mini-empty">No changes yet.</div>
                ) : (
                  <div className="tab-scroll-list">
                    <div className="timeline-list">
                      {[...selectedOrderDetail.history].reverse().map((event) => (
                        <article className="timeline-item" key={event.id}>
                          <strong>{historySummary(event)}</strong>
                          <span>
                            {event.changed_by.name} - {formatTimestamp(event.changed_at)}
                          </span>
                          <p>{event.reason}</p>
                        </article>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}

            {activeTab === "comments" && (
              <div className="tab-panel-stack">
                {isOperator && !selectedOrderIsQueuedDraft && (
                  <form className="comment-form" onSubmit={onAddComment}>
                    <label className="field field-wide">
                      <span>Add a comment</span>
                      <textarea
                        value={commentDraft}
                        onChange={(event) => onCommentDraftChange(event.target.value)}
                        placeholder="Leave context for the next operator."
                        rows={3}
                      />
                    </label>
                    <button
                      className="primary-action"
                      disabled={isSubmitting || commentDraft.trim().length === 0}
                      type="submit"
                    >
                      {isSubmitting ? "Posting..." : "Add comment"}
                    </button>
                  </form>
                )}

                {isOperator && selectedOrderIsQueuedDraft && (
                  <div className="mini-empty">
                    Sync this order before adding comments.
                  </div>
                )}

                {!isOperator && (
                  <div className="mini-empty">
                    No customer messages yet. Use support for updates that should not wait.
                    <a className="secondary-action customer-support-action" href={supportHref}>
                      Contact support
                    </a>
                  </div>
                )}

                {visibleComments.length === 0 && isOperator && !selectedOrderIsQueuedDraft ? (
                  <div className="mini-empty">No comments yet.</div>
                ) : isOperator ? (
                  <div className="tab-scroll-list">
                    <div className="timeline-list">
                      {[...visibleComments].reverse().map((comment) => (
                        <article className="timeline-item" key={comment.id}>
                          <strong>{comment.author.name}</strong>
                          <span>{formatTimestamp(comment.created_at)}</span>
                          <p>{comment.body}</p>
                        </article>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            )}
          </div>
        </article>
      )}
    </section>
  );
}
