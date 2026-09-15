# V2 release hardening

```yaml
status: current
canonical_for: v2-release-plan
last_verified: 2026-09-15
```

Status: not started — blocked on [catalogue.md](catalogue.md) and [frontend.md](frontend.md).

## V2-16: Cross-feature accessibility and responsive review

- Test icon names, dialogs, focus behavior, heading order, alt text, keyboard navigation, contrast, and reduced motion.
- Exercise cluster/list, filters, credit rows, People rows, and comments at mobile and desktop widths.
- Add automated checks where stable and record any manual verification.

## V2-17: End-to-end and operational verification

- Extend the Playwright journey to cover filters, view switching, photo rendering, comment creation, and credit icon actions.
- Add a Compose smoke test covering MinIO readiness and media persistence across service restart.
- Run backend tests, frontend checks/tests/build, importer tests, container builds, and clean-clone setup.
- Confirm no backend address, MinIO credential, or object-store URL leaks into browser code.

## V2-18: Documentation and release checklist

- Update root README setup/configuration/reset/backup instructions for MinIO.
- Keep [architecture/overview.md](../../architecture/overview.md), [architecture/code-map.md](../../architecture/code-map.md), [requirements.md](../../product/requirements.md), and ADRs synchronized.
- Record any later hard-to-reverse v2 decisions in new ADRs.
- Update [verification/v2-acceptance.md](../../verification/v2-acceptance.md) with test/evidence links as each item above lands.
