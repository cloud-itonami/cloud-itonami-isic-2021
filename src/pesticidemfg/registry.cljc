(ns pesticidemfg.registry
  "Pure-function domain logic for the pesticide and other agrochemical
  product plant-operations coordination actor -- equipment/batch
  verification, shipment-weight recompute, product-type validation,
  active-ingredient-concentration plausibility validation,
  active-ingredient-concentration label-ceiling validation, and draft
  maintenance-schedule/shipment-coordination record construction.

  Per docs/adr/0001-architecture.md Decision 1: this vertical has NO
  pre-existing `kotoba-lang/pesticidemfg`-style capability library to
  wrap (verified: no such repo exists). The domain logic therefore
  lives here as pure functions, re-verified INDEPENDENTLY by
  `pesticidemfg.governor` -- the same 'ground truth, not self-report'
  discipline every sibling actor's own registry establishes (e.g.
  `paintmfg.registry/shipment-weight-exceeded?` from
  `cloud-itonami-isic-2022`, this actor's closest chemical-process-
  plant analog): never trust a proposal's own self-reported weight/
  status/active-ingredient-concentration when the inputs needed to
  recompute it independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant-operations system, and NO pesticide-
  registration or label-approval decision authority whatsoever. It
  builds the DRAFT record a plant coordinator would keep (a scheduled
  maintenance window, a coordinated shipment), not the act of actuating
  a formulation/mixing line, dispatching a real freight carrier, or
  granting/deciding a pesticide registration or product-label approval
  (this actor NEVER does any of these -- see README `What this actor
  does NOT do`).

  SCOPE note: ISIC 2021 covers pesticides and other agrochemical
  products -- herbicides, insecticides, fungicides, rodenticides,
  nematicides, molluscicides, fumigants, plant-growth regulators,
  desiccants/defoliants, and tank-mix adjuvants -- one plant-operations
  shape (formulation/mixing via blending tanks and high-shear mixers,
  plus filling/packaging lines) spanning several product families, the
  same combined-scope pattern `cloud-itonami-isic-2022`'s own paint/
  varnish/coating/printing-ink/mastic scope establishes. Every batch's
  own active-ingredient concentration is subject to a real, product-
  registered label ceiling (modeled on representative U.S. EPA / Canada
  PMRA maximum labeled active-ingredient concentration ranges per
  product category) that varies by product category --
  `active-ingredient-exceeds-label-limit?` independently re-verifies a
  batch's own declared active-ingredient concentration against that
  closed ceiling table, never taken on the advisor's self-report, the
  same discipline `paintmfg.registry/voc-content-exceeds-limit?`
  applies to its own regulatory disclosure obligation. CRITICAL: this
  ceiling check independently re-derives whether an ALREADY-REGISTERED
  product's own batch stays within its own label -- it never grants,
  revokes, or otherwise decides pesticide registration or label-
  approval status. That decision is exclusively a regulatory
  authority's (e.g. U.S. EPA, Canada PMRA, EU) call, never this
  actor's -- see `pesticidemfg.governor`'s
  `registration-decision-blocked-violations`, a permanent, unconditional
  block on any proposal that attempts one.")

;; ----------------------------- constants -----------------------------

(def valid-product-types
  "The closed set of product-type values a production-batch record may
  declare -- spanning ISIC 2021's own combined scope: pesticides and
  other agrochemical products. Anything else is a fabricated/
  unrecognized product type -- the governor HARD-holds rather than let
  an invented product type pass through."
  #{:herbicide :insecticide :fungicide :rodenticide :nematicide
    :molluscicide :fumigant :plant-growth-regulator
    :desiccant-defoliant :adjuvant})

(def active-ingredient-label-limit-pct
  "The closed active-ingredient-concentration regulatory label ceiling
  table (percent active ingredient by weight of the ready-to-ship
  formulated product), one entry per `valid-product-types` member --
  modeled on REPRESENTATIVE U.S. EPA / Canada PMRA maximum labeled
  active-ingredient concentration ranges per formulation category.
  Illustrative, representative values (not an exhaustive multi-
  jurisdiction regulatory database -- see README `What this actor does
  NOT do`), the same 'representative subset, not exhaustive' scope
  `paintmfg.registry/voc-limit-g-per-l` establishes for its own
  regulatory set."
  {:herbicide               62.0
   :insecticide              25.0
   :fungicide                50.0
   :rodenticide               5.0
   :nematicide               40.0
   :molluscicide              3.0
   :fumigant                 98.0
   :plant-growth-regulator   20.0
   :desiccant-defoliant      45.0
   :adjuvant                 10.0})

(def active-ingredient-min-pct
  "Physical floor for a batch's own active-ingredient-concentration
  reading (percent by weight) -- a real formulated pesticide batch is
  never a zero-active-ingredient product (that would be an inert
  carrier, not a pesticide batch)."
  0.0)

(def active-ingredient-max-pct
  "Physical ceiling for a batch's own active-ingredient-concentration
  reading (percent by weight) -- 100% is a technical (unformulated)
  active-ingredient concentrate, the physical maximum possible. A
  reading beyond this is implausible sensor/QC data, not a real batch."
  100.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the plant's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its product-type/weight/active-ingredient-concentration claims
  have actually been QC-inspected, not merely logged from an
  unverified intake patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the plant's
  production ledger? Coordinating a shipment against a batch that is
  not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-weight-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal:
  would `shipped-to-date-kg` + `new-weight-kg` exceed `batch`'s own
  recorded `:weight-kg` (the batch's own logged production weight)?
  Needs no proposal inspection or stored-verdict lookup -- its inputs
  are permanent fields already on the batch's own record, the same
  shape every sibling actor's own cost/total-matching check uses."
  [batch new-weight-kg]
  (let [capacity (:weight-kg batch)
        so-far (:shipped-weight-kg batch 0.0)]
    (and (number? capacity)
         (number? new-weight-kg)
         (> (+ (double so-far) (double new-weight-kg)) (double capacity)))))

(defn product-type-valid?
  "Is `product-type` one of the closed, known product-type values
  (herbicide, insecticide, fungicide, rodenticide, nematicide,
  molluscicide, fumigant, plant-growth regulator, desiccant/defoliant,
  or adjuvant)? nil/blank is treated as invalid (a production-batch
  patch must declare a real product type, not omit it silently)."
  [product-type]
  (contains? valid-product-types product-type))

(defn active-ingredient-valid?
  "Is `active-ingredient-pct` a physically plausible active-ingredient-
  concentration reading (percent by weight)? Rejects nil, non-numbers,
  negative values, and values beyond `active-ingredient-max-pct` -- a
  fabricated or sensor-error reading, never let through as a real
  batch fact."
  [active-ingredient-pct]
  (and (number? active-ingredient-pct)
       (>= (double active-ingredient-pct) active-ingredient-min-pct)
       (<= (double active-ingredient-pct) active-ingredient-max-pct)))

;; ----------------------------- active-ingredient label-ceiling checks -----------------------------

(defn active-ingredient-label-limit-for [product-type]
  (get active-ingredient-label-limit-pct product-type))

(defn active-ingredient-exceeds-label-limit?
  "Ground-truth check for a `:log-production-batch` proposal:
  INDEPENDENTLY re-derive the EFFECTIVE product type (patch's own
  `:product-type`, else the batch's already-recorded type) and check
  whether the patch's own declared `:active-ingredient-pct` exceeds
  that product type's own closed regulatory label ceiling
  (`active-ingredient-label-limit-pct`) -- never taken on the
  advisor's self-report that the formulation 'is within label'.
  Modeled on representative U.S. EPA / Canada PMRA maximum labeled
  active-ingredient concentration ranges. `effective-product-type`
  with no known ceiling (not in `valid-product-types`) is NOT
  independently flagged here -- `invalid-product-type-violations` in
  `pesticidemfg.governor` already rejects a fabricated product type on
  its own. NOTE: this re-derives whether an ALREADY-REGISTERED
  product's own batch stays within its own label -- it does not grant,
  revoke, or decide registration/label-approval status (see ns
  docstring, and `pesticidemfg.governor`'s
  `registration-decision-blocked-violations`)."
  [effective-product-type active-ingredient-pct]
  (boolean
   (and (some? active-ingredient-pct)
        (let [limit (active-ingredient-label-limit-for effective-product-type)]
          (and (some? limit) (> (double active-ingredient-pct) (double limit)))))))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's/shipping approver's act, not this
  actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  formulation/mixing (blending tank, high-shear mixer) or filling/
  packaging-line maintenance window against a verified, registered
  piece of equipment. Pure function -- does not actuate the
  formulation/mixing/packaging line or execute any maintenance; it
  builds the RECORD a plant coordinator would keep.
  `pesticidemfg.governor` independently re-verifies the equipment's
  own verified/registered ground truth, and permanently blocks any
  attempt to directly actuate the formulation/mixing line (see README
  `Actuation`), before this is ever allowed to commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound pesticide/agrochemical-product shipment against a verified,
  registered production batch. Pure function -- does not dispatch any
  real freight carrier; it builds the RECORD a plant coordinator would
  keep. `pesticidemfg.governor` independently re-verifies the
  shipment's own claimed weight against `shipment-weight-exceeded?`,
  before this is ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
