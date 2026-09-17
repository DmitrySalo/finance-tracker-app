# Инструкции frontend

## Обязательный порядок работы

В дополнение к корневому `AGENTS.md` перед изменением frontend-кода:

1. Прочитай `.opencode/instructions/frontend-style.md`.
2. Если задача затрагивает тесты — прочитай `.opencode/instructions/frontend-testing.md`.
3. Если задача затрагивает HTTP API — прочитай `.opencode/instructions/api-contracts.md`.
4. Если задача затрагивает безопасность, аутентификацию или авторизацию — прочитай `.opencode/instructions/security.md`.

## Технологии и структура

- Frontend: React 19 и TypeScript 5.9 согласно `ARCHITECTURE.md`; используй Node.js 24 LTS или совместимую версию из `frontend/package.json`.
- Сохраняй feature-based структуру `app`, `shared`, `features` и `pages` из `ARCHITECTURE.md`.
- Server state ведёт TanStack Query. Локальное UI-state и состояние форм не смешивай с server state.
- Используй централизованный API-клиент. Не выполняй HTTP-запросы из UI-компонентов напрямую.
- Сохраняй адаптивность, семантическую HTML-разметку, доступность с клавиатуры, видимый focus и состояния loading, empty, error, success.
- Не добавляй frontend-зависимости, если задачу можно решить средствами TypeScript, React или уже подключённых библиотек.

## Проверки

- После изменений запускай релевантные npm-команды из `frontend/`.
- Полная frontend-проверка: `npm run lint`, `npm run test` и `npm run build`.
