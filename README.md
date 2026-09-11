# cloud-itonami-isic-2021: Manufacture of pesticides and other agrochemical products

Open Business Blueprint for **ISIC Rev.5 2021**: manufacture of pesticides and other agrochemical products — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office **plant operations**: production-batch data logging (product-type/weight/active-ingredient-concentration), formulation/mixing-equipment (blending tank, high-shear mixer) and filling/packaging-line maintenance scheduling, safety-concern flagging, and outbound shipment coordination.

This repository designs a forkable OSS business for pesticide/
agrochemical plant operations: run by a qualified operator so a plant
keeps its own operating records instead of renting a closed SaaS.

## Scope: one plant-operations shape, several related product families

ISIC 2021 covers a single manufacturing shape spanning several related
product families: **herbicides**, **insecticides**, **fungicides**,
**rodenticides**, **nematicides**, **molluscicides**, **fumigants**,
**plant-growth regulators**, **desiccants/defoliants**, and **tank-mix
adjuvants**. Every family shares the same formulation/mixing
(blending tank or high-shear mixer) + filling/packaging-line plant
shape and the same back-office coordination actor design (verified/
registered equipment+batch gate, permanent equipment-actuation block)
— this repo does not split them into separate actors. This is
distinct from a chemical-process primary-forms plant (e.g.
`cloud-itonami-isic-2013`) or a paint/coatings plant (e.g.
`cloud-itonami-isic-2022`): this plant's own hazard profile is
chemical (active-ingredient toxicity/exposure risk and environmental-
contamination risk during formulation/mixing) AND regulatory
(active-ingredient-concentration label-ceiling compliance for
essentially every product category, modeled on representative U.S. EPA
/ Canada PMRA maximum labeled active-ingredient concentration ranges),
not a saponification, polymerization-reactor, or VOC-emission hazard.

## What this actor does

Proposes **plant operations coordination**, not equipment operation or regulatory decision-making:
- `:log-production-batch` — formulation/mixing batch, active-ingredient-concentration data logging (administrative, not an operational decision)
- `:schedule-maintenance` — formulation/mixing-equipment (blending tank, high-shear mixer) or filling/packaging-line maintenance scheduling proposal
- `:flag-safety-concern` — surface a chemical-hazard (toxicity/exposure risk)/environmental-contamination concern (always escalates)
- `:coordinate-shipment` — outbound product shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical, regulated domain**
(formulation/mixing-equipment and filling/packaging-line equipment,
active-ingredient toxicity/exposure chemical hazard, environmental-
contamination risk, pesticide-registration and label-approval
regulatory authority):

- Does NOT control formulation/mixing-equipment or filling/packaging-line equipment directly
- Does NOT make plant-safety or product-safety decisions (that's the plant supervisor's exclusive human authority)
- Does NOT actuate the formulation/mixing or filling/packaging line (human plant supervisor decides)
- Does NOT decide, grant, or revoke a pesticide registration or product-label approval — that is EXCLUSIVELY a regulatory authority's (e.g. U.S. EPA, Canada PMRA, EU) call, never this actor's
- ONLY proposes/coordinates operations back-office; all actuation and all registration/label decisions require the appropriate human or regulatory authority
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`pesticidemfg.operation/build`, a langgraph-clj StateGraph):
1. **`pesticidemfg.advisor`** (sealed intelligence node, `PesticideAdvisor`): proposes decisions only, never commits
2. **`pesticidemfg.governor`** (independent, `Pesticide & Agrochemical Plant Operations Governor`): validates against domain rules, re-derived from `pesticidemfg.registry`'s pure functions and `pesticidemfg.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct formulation/mixing-line-equipment control)
     - Directly actuating the formulation/mixing or filling/packaging line (`:actuate-line? true`) is a PERMANENT, unconditional block
     - Deciding or granting a pesticide registration or product-label approval (`:decide-registration? true`) is a PERMANENT, unconditional block — exclusively a regulatory authority's call
     - A shipment may not push a batch's own recorded shipped weight past its own logged production weight (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:product-type` value on a production-batch patch
     - No physically implausible `:active-ingredient-pct` value on a production-batch patch
     - A batch's own declared `:active-ingredient-pct` must independently stay within its product type's own regulatory label ceiling — never taken on the advisor's self-report that the formulation "is within label"
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`pesticidemfg.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`pesticidemfg.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
kbb -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
kbb -M:dev:test

# Run the demo
kbb -M:dev:run

# Lint
kbb -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
