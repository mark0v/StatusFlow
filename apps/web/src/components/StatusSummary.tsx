import { statusLabels, type OrderStatus } from "../data/webTypes";

interface Props {
  activeStatusFilter: OrderStatus | null;
  groupedStatuses: readonly (readonly [OrderStatus, number])[];
  totalCount: number;
  onStatusFilterChange: (status: OrderStatus | null) => void;
}

export function StatusSummary({
  activeStatusFilter,
  groupedStatuses,
  totalCount,
  onStatusFilterChange
}: Props) {
  return (
    <div className="summary-strip">
      <button
        className={`status-card status-card-filter ${activeStatusFilter === null ? "active" : ""}`}
        onClick={() => onStatusFilterChange(null)}
        type="button"
      >
        <span>Total</span>
        <strong>{totalCount}</strong>
      </button>
      {groupedStatuses.map(([status, count]) => (
        <button
          className={`status-card status-card-filter ${activeStatusFilter === status ? "active" : ""}`}
          key={status}
          onClick={() => onStatusFilterChange(status)}
          type="button"
        >
          <span>{statusLabels[status]}</span>
          <strong>{count}</strong>
        </button>
      ))}
    </div>
  );
}
