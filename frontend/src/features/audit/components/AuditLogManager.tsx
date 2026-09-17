import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { AuditEntityType, AuditLog } from "../../../shared/api/models";
import { useLocalization } from "../../../shared/localization/LocalizationProvider";
import { formatDecimal } from "../../../shared/formatters/formatDecimal";
import { auditLogQueryKey, listAuditLogs } from "../api/auditApi";
import styles from "./AuditLogManager.module.css";

function auditActionLabel(action: AuditLog["action"], l: (english: string, russian: string) => string): string {
  if (action === "CREATE") return l("Created", "Создано");
  if (action === "UPDATE") return l("Updated", "Изменено");
  return l("Deleted", "Удалено");
}

function auditEntityLabel(entityType: AuditLog["entityType"], l: (english: string, russian: string) => string): string {
  return entityType === "TRANSACTION" ? l("Transaction", "Операция") : l("Budget", "Бюджет");
}

function auditFieldLabel(field: string, l: (english: string, russian: string) => string): string {
  const labels: Record<string, [string, string]> = {
    amount: ["Amount", "Сумма"], budgetMonth: ["Budget month", "Месяц бюджета"], categoryId: ["Category ID", "Идентификатор категории"], currency: ["Currency", "Валюта"], description: ["Description", "Описание"], exchangeRateToBase: ["Exchange rate to base currency", "Курс к основной валюте"], limitAmount: ["Limit amount", "Лимит"], transactionDate: ["Transaction date", "Дата операции"], transactionType: ["Transaction type", "Тип операции"],
  };
  const label = labels[field];
  return label === undefined ? field : l(...label);
}

function stateValue(field: string, value: string | null): string | null {
  if (value === null || !["amount", "limitAmount"].includes(field)) return value;
  return formatDecimal(value);
}

function State({ label, state }: { label: string; state: AuditLog["beforeState"] }) {
  const { l } = useLocalization();
  return <section className={styles.state}><h3>{label}</h3>{state === null ? <p>{l("Not available", "Недоступно")}</p> : <dl>{Object.entries(state).map(([key, value]) => <div key={key}><dt>{auditFieldLabel(key, l)}</dt><dd>{stateValue(key, value) ?? l("Not set", "Не задано")}</dd></div>)}</dl>}</section>;
}

export function AuditLogManager() {
  const { l } = useLocalization();
  const [entityType, setEntityType] = useState<AuditEntityType>("TRANSACTION");
  const [entityId, setEntityId] = useState("");
  const [submittedEntityId, setSubmittedEntityId] = useState("");
  const [page, setPage] = useState(0);
  const auditQuery = useQuery({ queryKey: [...auditLogQueryKey, entityType, submittedEntityId, page], queryFn: ({ signal }) => listAuditLogs(entityType, submittedEntityId, page, signal) });

  return <section className={styles.page}><header className={styles.header}><div><h1>{l("Audit log", "Журнал аудита")}</h1><p>{l("Review changes to your transactions and budgets.", "Просматривайте изменения операций и бюджетов.")}</p></div></header><form className={styles.filters} onSubmit={(event) => { event.preventDefault(); setSubmittedEntityId(entityId); setPage(0); }}><div><label htmlFor="audit-entity-type">{l("Resource type", "Тип ресурса")}</label><select id="audit-entity-type" onChange={(event) => { setEntityType(event.target.value === "BUDGET" ? "BUDGET" : "TRANSACTION"); setPage(0); }} value={entityType}><option value="TRANSACTION">{l("Transactions", "Операции")}</option><option value="BUDGET">{l("Budgets", "Бюджеты")}</option></select></div><div><label htmlFor="audit-entity-id">{l("Resource ID", "Идентификатор ресурса")}</label><input id="audit-entity-id" onChange={(event) => setEntityId(event.target.value)} placeholder={l("Optional UUID", "Необязательный UUID")} type="text" value={entityId} /></div><button type="submit">{l("Apply filters", "Применить фильтры")}</button></form>{auditQuery.isPending && <p role="status">{l("Loading audit log…", "Загрузка журнала аудита…")}</p>}{auditQuery.isError && <p className={styles.error} role="alert">{l("We could not load the audit log. Please refresh the page.", "Не удалось загрузить журнал аудита. Обновите страницу.")}</p>}{auditQuery.data?.items.length === 0 && <p className={styles.empty}>{l("No audit entries match these filters.", "Нет записей аудита, соответствующих этим фильтрам.")}</p>}{auditQuery.data !== undefined && auditQuery.data.items.length > 0 && <ol className={styles.entries}>{auditQuery.data.items.map((entry) => <li key={entry.id}><header><strong>{auditActionLabel(entry.action, l)}</strong><span>{auditEntityLabel(entry.entityType, l)} · {entry.occurredAt}</span></header><p>{l(`Resource: ${entry.entityId}`, `Ресурс: ${entry.entityId}`)}</p><div className={styles.states}><State label={l("Before", "До")} state={entry.beforeState} /><State label={l("After", "После")} state={entry.afterState} /></div></li>)}</ol>}{auditQuery.data !== undefined && auditQuery.data.page.totalPages > 1 && <nav aria-label={l("Audit log pages", "Страницы журнала аудита")} className={styles.pagination}><button disabled={page === 0} onClick={() => setPage((value) => value - 1)} type="button">{l("Previous page", "Предыдущая страница")}</button><span>{l(`Page ${page + 1} of ${auditQuery.data.page.totalPages}`, `Страница ${page + 1} из ${auditQuery.data.page.totalPages}`)}</span><button disabled={page + 1 === auditQuery.data.page.totalPages} onClick={() => setPage((value) => value + 1)} type="button">{l("Next page", "Следующая страница")}</button></nav>}</section>;
}
