# Engineering Todo

## Sync

- [ ] Add a remote-change sync trigger for clients.
  Scope: use polling, WebSocket, push, or another notification-only mechanism so the app can automatically pull when server-side changes happen while the app is idle.
  Constraint: follow [sync.md](./sync.md); notifications may only say "something changed, please pull" and must not bypass the normal pull/merge flow.
