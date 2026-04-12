import {
  formatTimestamp,
  historySummary,
  statusLabels,
  statusTone,
  type OrderCard,
  type OrderDetail,
  type OrderStatus
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
  return (
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
              Clear selection
            </button>
            {recoveryCandidateOrder ? (
              <button
                className="primary-action"
                onClick={onRecoverSelection}
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
              <form className="comment-form" onSubmit={onAddComment}>
                <label className="field field-wide">
                  <span>Add comment</span>
                  <textarea
                    value={commentDraft}
                    onChange={(event) => onCommentDraftChange(event.target.value)}
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
  );
}
