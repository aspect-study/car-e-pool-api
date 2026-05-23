# ADR-005: Vehicle Plate Number Masking

**Status:** Accepted  
**Date:** 2024  
**Deciders:** Product + Legal

## Context

Vehicle plate numbers are personally identifiable information in many jurisdictions. Displaying a full plate number in a Telegram group (visible to all group members) or in API responses could expose driver identity to unintended parties — especially given that the ride announcement is posted to a shared group topic.

## Decision

Vehicle plate numbers are **always masked** before leaving the service layer. The masking rule: first 3 characters visible, remainder replaced with `***`.

```java
// Applied in VehicleService before constructing VehicleResponse DTO
String masked = plateNumber.substring(0, Math.min(3, plateNumber.length())) + "***";
```

The `VehicleResponse` DTO (which is what `EntityMapper` produces and what the bot/REST layer receives) always contains the masked value. No code outside `VehicleService` is responsible for masking.

## Consequences

- `carpool-bot` never receives a raw plate number — it cannot accidentally log or display one
- REST API responses (`/api/vehicles`, ride details) return masked plates
- The admin panel shows masked plates unless there is a specific "full plate" view with an explicit "ADMIN" role requirement
- Full plate numbers remain in the DB and in `Vehicle.plateNumber` — they are not hashed or encrypted at rest (out of scope for this ADR)
- If the masking algorithm needs to change, only `VehicleService` must be updated

## Enforcement

The `security.md` agent flags any code that constructs a `VehicleResponse` with a raw plate value, and any log line that contains a plate number pattern (`[A-Z0-9]{3,8}`).