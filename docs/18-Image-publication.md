# Image publication and homelab promotion

This repository owns source, tests and image publication. Homelab owns the
production Compose configuration, secrets, release selection and deployment to
edge-dmz. Publishing an image never logs into Endurance, Proxmox or edge-dmz and
never changes production traffic.

## Workflow

1. Pull requests run verification with read-only permissions and no publication
   credentials. Main pushes, a main-only manual invocation, and published releases
   can request image publication. A release commit must belong to main history.
2. Verification builds one Linux/amd64 image with OCI source repository and
   40-character source revision labels, then checks that runtime.
3. The successful verification job exports the image archive and
   `verified-image.json`. The publication job downloads that exact artifact by
   its immutable GitHub artifact ID. It does not rebuild the image.
4. Before registry login, publication verifies platform, runtime user, labels and
   Docker image/config ID against the tested metadata. A rebuilt or substituted
   image is rejected.
5. Only the publication job receives `packages: write`. It uses its own
   `GITHUB_TOKEN` through stdin; no PAT, SSH key, server password or application
   secret is required. Existing package permissions must allow this repository
   to write the package; package visibility is not changed by the workflow.
6. Publication pushes `sha-<commit>` and records the registry digest. A published
   semantic-version release also adds its version alias. No `latest` tag is
   updated. Tags can move on a rebuild; **only the digest is the immutable
   deployment identity**.
7. `image-publication.json` is uploaded for 90 days and its immutable reference is
   shown in the run summary. Homelab reviews the receipt, pulls/verifies the image
   and records its selected digest in the release operation before deployment.

The receipt has API version `homelab.image-publication/v1`, repository,
`image` (`ghcr.io/...@sha256:...`), registry `digest`, `source_revision`,
`source_repository`, `config_digest`, `platform`, `runtime_user` and `commit_tag`.
It contains no environment variables, credentials or deployment approval.
OCI labels and a tested artifact establish this workflow's identity checks;
they are not a claim of signed provenance or a cryptographic supply-chain attestation.

## Operating it

After these workflow changes are reviewed and merged, use the normal main build
or manually run **Verify and publish image for homelab** on `main`. Select the
receipt from that run rather than resolving `latest`. Never copy application
secrets into workflow inputs or the build context.

The maintainer reviews/merges these workflow changes and controls release tags.
Once merged, a main push can publish an image automatically, without deployment.
The pipeline does not promote its own output to production. Future Jenkins
promotion must consume homelab's existing deployment contract; it must not
reimplement a separate Compose deployment in this repository.

## Local checks

- Run the repository's normal frontend/Maven verification in its devcontainer.
- Run `python3 -m unittest discover -s scripts/tests -v` for publication identity
  tests. They do not contact Docker or GHCR.
- Runtime smoke uses only an explicitly chosen local test image, isolated
  resources and synthetic data. No production configuration is loaded.
- Validate workflows with actionlint. A local pass does not mean the modified
  hosted workflow has run; its artifact transfer and GHCR publication must also
  be observed after review/merge.

Local images with the all-zero revision used during workflow preparation are
fixtures, never deployment candidates or production publication receipts.

For a read-only hosted handoff rehearsal, manually run **Pull request tests**
on the reviewed branch with `verify_release_artifact=true`. It exports, downloads
and verifies the image by artifact ID without registry login or packages write.
