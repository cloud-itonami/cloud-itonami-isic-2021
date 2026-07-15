# Governance

`cloud-itonami-isic-2021` is an OSS open-business blueprint for pesticides and other agrochemical products plant operations coordination.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a formulation/mixing-equipment or filling/packaging-line action the governor refuses is never dispatched to hardware.
- the Pesticide & Agrochemical Plant Operations Governor remains independent of the advisor.
- hard policy violations (equipment-control bypass, line actuation, pesticide-registration/label-approval decision-making, record-suppression, unauthorized disclosure) cannot be overridden by human approval.
- every schedule, sign-off, record and disclose path is auditable.
- sensitive operating and personal data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing formulation/mixing-line-control or record policy checks
- claiming or exercising pesticide-registration/label-approval authority this actor does not have
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
