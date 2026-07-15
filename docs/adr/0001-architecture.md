# ADR-0001: PesticideAdvisor ⊣ Pesticide & Agrochemical Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-2021` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-2021` publishes an OSS blueprint for pesticides and
other agrochemical products **plant operations coordination**
(production-batch product-type/weight/active-ingredient-concentration
data logging, formulation/mixing-equipment and filling/packaging-line
maintenance scheduling, safety-concern flagging, and outbound shipment
coordination). Like every actor in this fleet, the blueprint alone is
not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established across the cloud-itonami fleet.

The closest domain analog is `cloud-itonami-isic-2022` (Manufacture of
paints, varnishes and similar coatings, printing ink and mastics):
both are back-office coordination actors for a fixed processing PLANT
with heavy manufacturing equipment and a real physical safety
dimension, and both share the same four-op shape
(`:log-production-batch`/`:schedule-maintenance`/`:flag-safety-
concern`/`:coordinate-shipment`) and the same two-entity verified/
registered gate structure (equipment for maintenance scheduling, batch
for shipment coordination). The two verticals are, however, distinct
plants with distinct hazard AND regulatory profiles: 2022's hazard is
solvent-VOC exposure and flammability during pigment dispersion/mixing
plus a VOC-content regulatory ceiling obligation that applies to every
product type in its scope, while 2021's hazard is active-ingredient
toxicity/exposure risk and environmental-contamination risk during
formulation/mixing AND a pesticide-registration/label-approval
regulatory structure that this actor must explicitly stay OUT of
(never decide or grant registration/label status -- that is
exclusively a regulatory authority's call), in addition to an
active-ingredient-concentration label-ceiling obligation analogous to
2022's VOC ceiling. This build mirrors 2022's architecture closely but
adapts the hazard profile, equipment/product vocabulary, and adds one
new domain-specific PERMANENT governor block
(`registration-decision-blocked-violations`, the domain-specific twin
of `line-actuate-blocked-violations`) alongside two domain-specific
validation checks (active-ingredient-concentration plausibility and
active-ingredient-concentration label-ceiling) in place of 2022's
viscosity/fineness-of-grind/VOC-content triple.

This vertical has NO pre-existing `kotoba-lang/pesticidemfg`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic -- pure functions in
`pesticidemfg.registry` (equipment/batch verification, shipment-weight
recompute, product-type validation, active-ingredient-concentration
plausibility validation, active-ingredient-concentration label-ceiling
validation) are re-verified independently by the governor, the same
"ground truth, not self-report" discipline established across prior
actors (most directly `cloud-itonami-isic-2022`'s `paintmfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:pesticide-agrochemical-plant-operations-governor`, is grep-verified
UNIQUE fleet-wide (`gh search code
"pesticide-agrochemical-plant-operations-governor" --owner
cloud-itonami`, zero hits before this repo was created); so is the
`pesticidemfg` namespace prefix (`gh search code "pesticidemfg"
--owner cloud-itonami`, zero hits).

## Decision

### Decision 1: Self-contained domain logic (no external pesticide/agrochemical-manufacturing capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
pesticide/agrochemical vertical has NO pre-existing capability library
to wrap. The equipment/batch-verification / shipment-weight /
product-type / active-ingredient-concentration validation functions
live as pure functions in `pesticidemfg.registry` and are re-verified
independently by `pesticidemfg.governor` -- the same "ground truth, not
self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-2022`'s `paintmfg.registry`).

### Decision 2: Coordination, not control, and NOT a registration authority — scope boundary at the back-office

This actor is **strictly back-office coordination** of pesticide/
agrochemical plant operations. It does NOT:
- Control formulation/mixing-equipment (blending tank, high-shear mixer) or filling/packaging-line equipment directly
- Make plant-safety or product-safety decisions (exclusive to the human plant supervisor)
- Actuate the formulation/mixing or filling/packaging line
- Decide, grant, or revoke a pesticide registration or product-label approval (exclusive to a regulatory authority, e.g. U.S. EPA, Canada PMRA, EU)

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority or
a regulatory authority's registration process — it is a
proposal-screening and documentation layer.

**CRITICAL SAFETY BOUNDARY**: pesticide/agrochemical manufacturing is
a safety-critical and heavily regulated domain (active-ingredient
toxicity/exposure chemical hazard, environmental-contamination risk,
pesticide-registration/label-approval regulatory structure). Safety-
concern flagging NEVER auto-commits. All safety concerns escalate
immediately to human review, and no proposal may ever attempt a
registration/label-approval decision, regardless of confidence or
phase.

### Decision 3: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (chemical-hazard toxicity/exposure-risk
concern, environmental-contamination concern, equipment-safety
concern, crew exposure) ALWAYS escalates, never auto-commits. This is
not a "low-stakes proposal" — it is a circuit-breaker that must reach
human authority.

### Decision 4: Two independent verified/registered gates (equipment AND batch), not one

Like `cloud-itonami-isic-2022`, this vertical has TWO entity kinds
each gating a different op: `:schedule-maintenance` independently
verifies the referenced **equipment** unit's own `:verified?`/
`:registered?` fields; `:coordinate-shipment` independently verifies
the referenced **batch**'s own `:verified?`/`:registered?` fields.
Both are the same "plant/batch record must be independently
verified/registered before any action" HARD invariant applied to the
two distinct record kinds this domain actually has.
`:coordinate-shipment` additionally independently recomputes whether a
batch's own recorded shipped-to-date weight plus the proposal's own
claimed weight would exceed the batch's own recorded production
weight — never taken on the advisor's self-report.

### Decision 5: A second permanent block, distinct from line-actuate — the registration/label-approval-decision boundary

Unlike every prior chemical-process-plant sibling in this fleet, this
vertical sits inside an EXPLICIT product-registration regulatory
structure (a formulated pesticide product may only be manufactured and
sold once a regulatory authority has registered it and approved its
label). This creates scope-creep risk this actor must guard against
structurally, not just by omission: a compromised or mis-wired caller
could attempt to have this actor's `:log-production-batch` proposal
carry a `:decide-registration? true` flag, asking the actor to
"decide" that a batch's product is registered/label-approved.
`registration-decision-blocked-violations` in `pesticidemfg.governor`
HARD-blocks this unconditionally and permanently — structurally
identical in shape and severity to `line-actuate-blocked-violations`
(2022's `:actuate-line? true` block), but guarding the regulatory-
authority boundary instead of the physical-equipment boundary. Two
permanent, unconditional, non-overridable blocks instead of one is
this vertical's central architectural adaptation.

### Decision 6: Active-ingredient concentration — plausibility and label-ceiling, mirroring 2022's viscosity/VOC pair

Mirroring `cloud-itonami-isic-2022`'s viscosity-plausibility +
VOC-content-ceiling pair (itself analogous to
`cloud-itonami-isic-2013`'s absence of any such obligation), this
vertical adds TWO new governor checks over a single new domain fact
(`:active-ingredient-pct`):
- `:log-production-batch` INDEPENDENTLY re-validates a patch's own
  declared `:active-ingredient-pct` against a physically plausible
  range (0-100%, `pesticidemfg.registry/active-ingredient-valid?`) —
  a fabricated or sensor-error reading is rejected rather than let
  through.
- `:log-production-batch` INDEPENDENTLY re-derives the effective
  product type (patch's own `:product-type`, else the batch's
  already-recorded type) and, when the patch declares an
  `:active-ingredient-pct`, checks it against that product type's own
  closed regulatory label ceiling
  (`pesticidemfg.registry/active-ingredient-exceeds-label-limit?`,
  modeled on representative U.S. EPA / Canada PMRA maximum labeled
  active-ingredient concentration ranges) — this check independently
  re-derives whether an ALREADY-REGISTERED product's own batch stays
  within its own label; it does NOT grant, revoke, or decide
  registration/label-approval status (that decision boundary is
  Decision 5's block). This mirrors the "ground truth, not
  self-report" discipline every other governor check in this fleet
  establishes, applied to genuinely new domain-specific quality and
  regulatory facts this vertical's own product mix introduces.

### Decision 7: HARD invariants (no override)

Four HARD governor invariants (elaborated into twelve concrete checks
in `pesticidemfg.governor`, mirroring `cloud-itonami-isic-2022`'s own
elaboration of its HARD invariants into concrete checks, plus the one
new domain-specific permanent block per Decision 5 and the two new
domain-specific checks per Decision 6) block proposals and cannot be
overridden by human approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's weight must independently recompute within the batch's own logged production weight
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct formulation/mixing-line-equipment control, line actuation, or a pesticide-registration/label-approval-authority decision is permanently blocked
4. The op allowlist is closed — `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Pesticide/agrochemical plant operations back-office now has a
documented, governed, auditable coordination layer that funnels all
decisions through independent validation before human approval.

(+) The "coordination, not control, not registration authority"
boundary is explicit in code: all `:effect :propose`, all real-world
actuation requires human plant-supervisor sign-off, and no path exists
for this actor to decide a pesticide registration or label approval.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into twelve concrete governor checks) protect against scope creep into
unauthorized equipment operation, line actuation, registration/label-
approval decision-making, or non-compliant active-ingredient
concentration. Safety concerns are a circuit-breaker, not a threshold.

(+) Safety-critical and regulatory discipline is explicit:
safety-concern flagging cannot be rate-limited, suppressed, or
auto-decided by phase gate; active-ingredient-concentration label
compliance is independently re-verified against a closed per-product-
type ceiling, never taken on trust. Human review is mandatory for the
former; no review at all can ever grant the latter through this
actor.

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation and formulation/mixing/filling/
packaging-line operation remain human-controlled via external
channels, and pesticide registration/label approval remains a
regulatory-authority process entirely outside this actor.

(-) No integration with real plant-management databases (equipment
telemetry, batch tracking, freight dispatch, or an authoritative
multi-jurisdiction pesticide-registration/label database) — this is a
standalone coordinator blueprint; the closed
`active-ingredient-label-limit-pct` table is a representative,
illustrative subset of EPA/PMRA-style frameworks, not an exhaustive
multi-jurisdiction regulatory database.

## Verification

- `cloud-itonami-isic-2021`: `clojure -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `clojure -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-weight-exceeded, line-actuate-blocked,
  registration-decision-blocked, already-scheduled,
  invalid-product-type, invalid-active-ingredient,
  active-ingredient-exceeds-label-limit).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) — no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `clojure -M:test` resolves offline inside the monorepo checkout.
