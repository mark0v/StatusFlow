import { statusLabels, type OrderStatus } from "../data/webTypes";

interface Props {
  groupedStatuses: readonly (readonly [OrderStatus, number])[];
}

export function StatusSummary({ groupedStatuses }: Props) {
  return (
    <div className="summary-strip">
      {groupedStatuses.map(([status, count]) => (
        <article className="status-card" key={status}>
          <span>{statusLabels[status]}</span>
          <strong>{count}</strong>
        </article>
      ))}
    </div>
  );
}
