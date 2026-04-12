import {
  formatTimestamp,
  historySummary,
  statusLabels,
  statusTone,
  type OrderCard,
  type OrderDetail
} from "../data/webTypes";

interface Props {
  selectedOrderDetail: OrderDetail | null;
  detailError: string | null;
  isDetailLoading: boolean;
  isRefreshing: boolean;
  isSubmitting: boolean;
  isOperator: boolean;
  selectedOrderId: string | null;
  selectedOrderIsQueuedDraft: boolean;
  commentDraft: string;
  recoveryCandidateOrder: OrderCard | null;
  onRefresh: () => void;
  onClearSelection: () => void;
  onRecoverSelection: () => void;
  onCommentDraftChange: (draft: string) => void;
  onAddComment: (event: React.FormEvent<HTMLFormElement>) => void;
}

export function OrderInspector({
  selectedOrderDetail,
  detailError,
  isDetailLoading,
  isRefreshing,
  isSubmitting,
  isOperator,
  selectedOrderId,
  selectedOrderIsQueuedDraft,
  commentDraft,
  recoveryCandidateOrder,
  onRefresh,
  onClearSelection,
  onRecoverSelection,
  onCommentDraftChange,
  onAddComment
}: Props) {
  const visibleComments = isOperator ? selectedOrderDetail?.comments ?? [] : [];

  return (
    <section className="detail-inspector">
      {/* Nothing selected */}
      {!isDetailLoading && !detailError && !selectedOrderDetail && (
        <div className="inspector-empty">
          <p>Select an order to see its details.</p>
        </div>
      )}

      {/* Loading */}
      {isDetailLoading && (
        <div className="feedback-card feedback-sync">
          <span className="feedback-eyebrow">Loading</span>
          <strong>Fetching order detail</strong>
        </div>
      )}

      {/* Error — order unavailable */}
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

      {/* Order detail visible */}
      {!isDetailLoading && !detailError && selectedOrderDetail && (
        <div className="inspector-grid">
          <article className="inspector-card inspector-summary">
            <div className="inspector-topline">
              <span className="cell-code">{selectedOrderDetail.code}</span>
              <span className={`pill ${statusTone(selectedOrderDetail.status)}`}>
                {statusLabels[selectedOrderDetail.status]}
              </span>
            </div>
            <h4>{selectedOrderDetail.title}</h4>
            <p>{selectedOrderDetail.description || "No description yet."}</p>

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
                <h4>Status changes</h4>
              </div>
              <span className="inspector-count">{selectedOrderDetail.history.length}</span>
            </div>

            {selectedOrderDetail.history.length === 0 ? (
              <div className="mini-empty">No changes yet.</div>
            ) : (
              <div className="timeline-list">
                {[...selectedOrderDetail.history].reverse().map((event) => (
                  <article className="timeline-item" key={event.id}>
                    <strong>{historySummary(event)}</strong>
                    <span>
                      {event.changed_by.name} · {formatTimestamp(event.changed_at)}
                    </span>
                    <p>{event.reason}</p>
                  </article>
                ))}
              </div>
            )}
          </article>

          {isOperator && (
            <article className="inspector-card">
              <div className="inspector-section-heading">
                <div>
                  <p className="eyebrow">Notes</p>
                  <h4>Operator comments</h4>
                </div>
                <span className="inspector-count">{visibleComments.length}</span>
              </div>

              {!selectedOrderIsQueuedDraft && (
                <form className="comment-form" onSubmit={onAddComment}>
                  <label className="field field-wide">
                    <span>Add a note</span>
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
                    {isSubmitting ? "Posting..." : "Post"}
                  </button>
                </form>
              )}

              {selectedOrderIsQueuedDraft && (
                <div className="mini-empty">
                  Sync this order before adding comments.
                </div>
              )}

              {visibleComments.length === 0 && !selectedOrderIsQueuedDraft ? (
                <div className="mini-empty">No notes yet.</div>
              ) : (
                <div className="timeline-list">
                  {[...visibleComments].reverse().map((comment) => (
                    <article className="timeline-item" key={comment.id}>
                      <strong>{comment.author.name}</strong>
                      <span>{formatTimestamp(comment.created_at)}</span>
                      <p>{comment.body}</p>
                    </article>
                  ))}
                </div>
              )}
            </article>
          )}
        </div>
      )}
    </section>
  );
}
