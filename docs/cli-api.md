# WhaleDoc CLI API

This is the contract between the WhaleDoc CLI and the WhaleDoc API: the endpoints the CLI calls, what it sends, and what it expects back. The paths are defined in `ApiConstants`.

## General rules

**Base URL.** `https://api.whaledoc.io`, configurable at build time with `-Dwhaledoc.api.url=...`.

**Headers on every request.**

| Header | Value |
|---|---|
| `X-API-Version` | `v1` |
| `Accept` | `application/json`, or `text/event-stream` for event streams |
| `Authorization` | `Bearer <access token>`, on endpoints that require a login |

**Compatibility.** The CLI ignores unknown fields in JSON responses, so adding fields is always safe. Removing or renaming fields, or changing their meaning, breaks CLIs that are already installed and needs a new API version.

**Errors.** Any status outside 2xx is an error. The response body is shown to the user, so it should be a short, human-readable message. For event streams:

| Status | What the CLI does |
|---|---|
| `401` / `403` | Stops and asks the user to run `whaledoc login` again |
| `429`, `5xx` | Reconnects with exponential backoff (1 s, 2 s, 4 s … up to 30 s), giving up after 10 failed attempts in a row |
| other `4xx` | Stops and shows the error |

## Event streams

Event streams use [server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) with `Content-Type: text/event-stream`.

- Each event has an `event:` type, a JSON `data:` payload and, where events can be resumed, an `id:`.
- Lines starting with `:` are comments. Send one about every 15 seconds (e.g. `: keep-alive`) so proxies don't close idle connections.
- When the connection drops, the CLI reconnects and sends `Last-Event-ID` with the ID of the last event it received. The server should then send the events that happened after that one, so none are lost.
- The CLI ignores `retry:`; it uses its own backoff.

## Login

Login uses a device-style flow: the CLI starts a session, the user approves it in the browser, and the CLI receives the access token over an event stream.

1. The CLI creates a login session.
2. It shows the user an authentication code and opens the authorization URL in the browser.
3. The browser shows the same code. The user checks that they match and approves the login. This prevents someone from tricking a user into approving a session started on another machine.
4. The API sends the access token to the CLI over the session's event stream.

### Create a login session

`POST /cli/auth/sessions`

```json
{ "cliId": "3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d" }
```

`cliId` identifies the CLI installation. It is generated on first use and stays the same until the config is deleted.

Response `201 Created`:

```json
{
  "sessionId": "8c6f0e1a-...",
  "authorizationUrl": "https://app.whaledoc.io/cli/authorize/8c6f0e1a-...",
  "authCode": "ABCD-1234"
}
```

`authorizationUrl` must be an absolute `https` URL; the CLI only opens `http` and `https` URLs. Sessions should expire after 5 minutes, which is how long the CLI waits.

### Wait for approval

`GET /cli/auth/sessions/{sessionId}/events` (event stream, no `Authorization` header)

When the user approves the login, send one event and close the stream:

```
event: authenticated
data: {"accessToken": "..."}

```

Respond with `404` for unknown or expired sessions; the CLI stops waiting right away.

### Log out

`POST /cli/auth/logout` with `Authorization: Bearer <access token>` and no body.

Response `204 No Content`. The access token must stop working afterwards. The CLI only removes the token locally after this call succeeds.

## Webhook events

`GET /cli/webhooks/events` (event stream, requires `Authorization`)

Streams the webhook events of the user's account while `whaledoc listen` runs:

```
id: evt_01J9Z3K4XQ
event: document.created
data: {"id": "doc_123", "status": "created", ...}

```

- `event` is the webhook event type. The CLI currently knows `document.created`, `document.completed` and `document.failed`; it filters by type on its side, so the stream may contain all types.
- `data` is the same JSON payload a real webhook delivery would have.
- `id` must be unique and increasing so `Last-Event-ID` can resume the stream.

### Forwarding

With `--forward-to`, the CLI posts each event's `data` to the user's local URL with these headers:

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |
| `WhaleDoc-Event` | the event type, e.g. `document.created` |
| `WhaleDoc-Event-Id` | the event ID |

When real webhook deliveries are designed, they should use the same headers, so a local endpoint behaves the same way in development and production.
