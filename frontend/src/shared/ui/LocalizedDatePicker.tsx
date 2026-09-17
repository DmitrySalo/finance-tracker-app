import { forwardRef, useRef, useState } from "react";
import { useLocalization } from "../localization/LocalizationProvider";
import styles from "./LocalizedDatePicker.module.css";

type PickerType = "date" | "month";

interface LocalizedDatePickerProps {
  id: string;
  name?: string;
  onBlur?: () => void;
  type: PickerType;
  value: string;
  onChange: (value: string) => void;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
}

function localeTag(locale: "en" | "ru"): string { return locale === "ru" ? "ru-RU" : "en-US"; }
function parseValue(value: string): Date {
  const parts = value.split("-").map(Number);
  return new Date(Date.UTC(parts[0] || new Date().getUTCFullYear(), (parts[1] || 1) - 1, parts[2] || 1));
}
function toValue(date: Date, type: PickerType): string {
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const base = `${date.getUTCFullYear()}-${month}`;
  return type === "month" ? base : `${base}-${String(date.getUTCDate()).padStart(2, "0")}`;
}
function displayValue(value: string, type: PickerType, locale: "en" | "ru"): string {
  if (value === "") return "";
  return new Intl.DateTimeFormat(localeTag(locale), type === "month" ? { month: "long", year: "numeric", timeZone: "UTC" } : { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(parseValue(value));
}
function shiftMonth(date: Date, amount: number): Date { return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + amount, 1)); }

export const LocalizedDatePicker = forwardRef<HTMLInputElement, LocalizedDatePickerProps>(function LocalizedDatePicker({ id, name, onBlur, onChange, type, value, ...ariaProps }, ref) {
  const { l, locale } = useLocalization();
  const [isOpen, setIsOpen] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => parseValue(value));
  const trigger = useRef<HTMLInputElement>(null);
  const tag = localeTag(locale);
  const selected = value === "" ? undefined : parseValue(value);
  const title = new Intl.DateTimeFormat(tag, { month: "long", year: "numeric", timeZone: "UTC" }).format(visibleMonth);
  const weekdays = Array.from({ length: 7 }, (_, index) => new Intl.DateTimeFormat(tag, { weekday: "short", timeZone: "UTC" }).format(new Date(Date.UTC(2023, 0, 1 + index))));

  function close(): void { setIsOpen(false); trigger.current?.focus(); }
  function select(date: Date): void { onChange(toValue(date, type)); setVisibleMonth(date); close(); }
  const firstDay = new Date(Date.UTC(visibleMonth.getUTCFullYear(), visibleMonth.getUTCMonth(), 1));
  const daysInMonth = new Date(Date.UTC(visibleMonth.getUTCFullYear(), visibleMonth.getUTCMonth() + 1, 0)).getUTCDate();
  const blankDays = firstDay.getUTCDay();

  return <div className={styles.picker}>
    <div className={styles.inputRow}><input aria-describedby={ariaProps["aria-describedby"]} aria-expanded={isOpen} aria-haspopup="dialog" aria-invalid={ariaProps["aria-invalid"]} className={styles.trigger} id={id} name={name} onBlur={onBlur} onClick={() => setIsOpen((open) => !open)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setIsOpen((open) => !open); } }} readOnly ref={(element) => { trigger.current = element; if (typeof ref === "function") ref(element); else if (ref !== null) ref.current = element; }} type="text" value={displayValue(value, type, locale)} />{value !== "" && <button aria-label={l("Clear date", "Очистить дату")} className={styles.clear} onClick={() => onChange("")} type="button">×</button>}</div>
    {isOpen && <section aria-label={l("Calendar", "Календарь")} className={styles.dialog} onKeyDown={(event) => { if (event.key === "Escape") { event.preventDefault(); close(); } }} role="dialog">
      <div className={styles.header}><button aria-label={l("Previous month", "Предыдущий месяц")} onClick={() => setVisibleMonth((month) => shiftMonth(month, -1))} type="button">&lt;</button><p className={styles.title}>{title}</p><button aria-label={l("Next month", "Следующий месяц")} onClick={() => setVisibleMonth((month) => shiftMonth(month, 1))} type="button">&gt;</button></div>
      {type === "month" ? <div className={styles.months}>{Array.from({ length: 12 }, (_, index) => { const month = new Date(Date.UTC(visibleMonth.getUTCFullYear(), index, 1)); const label = new Intl.DateTimeFormat(tag, { month: "short", timeZone: "UTC" }).format(month); return <button aria-pressed={selected?.getUTCFullYear() === month.getUTCFullYear() && selected.getUTCMonth() === index} className={selected?.getUTCFullYear() === month.getUTCFullYear() && selected.getUTCMonth() === index ? styles.selected : undefined} key={index} onClick={() => select(month)} type="button">{label}</button>; })}</div> : <><div className={styles.weekdays}>{weekdays.map((weekday, index) => <span key={index}>{weekday}</span>)}</div><div className={styles.days}>{Array.from({ length: blankDays }, (_, index) => <span key={`blank-${index}`} />)}{Array.from({ length: daysInMonth }, (_, index) => { const day = new Date(Date.UTC(visibleMonth.getUTCFullYear(), visibleMonth.getUTCMonth(), index + 1)); const isSelected = selected !== undefined && toValue(day, "date") === toValue(selected, "date"); return <button aria-pressed={isSelected} aria-label={new Intl.DateTimeFormat(tag, { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" }).format(day)} className={isSelected ? styles.selected : undefined} key={index} onClick={() => select(day)} type="button">{index + 1}</button>; })}</div></>}
    </section>}
  </div>;
});
